package io.selene.app.downloading

import io.selene.app.model.DownloadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

/**
 * Moteur de téléchargement JVM, sans dépendance Android : il est testable hors appareil.
 *
 * Comportements couverts :
 *  - reprise après coupure, via `Range: bytes=N-` sur un fichier `.part` ;
 *  - serveur qui ignore `Range` (réponse 200) : on repart de zéro au lieu de corrompre le fichier ;
 *  - `416 Range Not Satisfiable` : le fichier est déjà complet ;
 *  - codes d'erreur transitoires (408, 429, 5xx) : nouvelle tentative avec backoff exponentiel ;
 *  - codes d'erreur définitifs (4xx) : échec immédiat, sans consommer les tentatives ;
 *  - annulation coopérative (le worker WorkManager peut être stoppé à tout moment).
 */
class DownloadEngine(
    private val client: OkHttpClient,
    private val maxAttempts: Int = 4,
    private val initialBackoffMs: Long = 500,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {

    suspend fun download(
        sourceUrl: String,
        target: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val part = File("${target.path}.part")

        var attempt = 0
        var lastError: DownloadException? = null

        while (attempt < maxAttempts) {
            coroutineContext.ensureActive()
            attempt++
            try {
                fetch(sourceUrl, part, headers, onProgress)
                replaceAtomically(part, target)
                return@withContext target
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: DownloadException) {
                lastError = error
                if (!error.retryable) throw error
            } catch (error: IOException) {
                lastError = DownloadException(
                    "Erreur réseau : ${error.message ?: error::class.simpleName}",
                    retryable = true,
                    cause = error,
                )
            }

            if (attempt < maxAttempts) {
                // Le serveur a le dernier mot : s'il annonce Retry-After, on l'écoute, dans la
                // limite de MAX_BACKOFF — insister plus tôt ne fait qu'aggraver la limitation.
                val exponential = initialBackoffMs * (1L shl (attempt - 1))
                val backoff = (lastError?.retryAfterMs ?: exponential).coerceIn(0L, MAX_BACKOFF_MS)
                sleeper(backoff)
            }
        }

        throw lastError ?: DownloadException("Téléchargement impossible", retryable = true)
    }

    private suspend fun fetch(
        sourceUrl: String,
        part: File,
        headers: Map<String, String>,
        onProgress: suspend (Long, Long?) -> Unit,
    ) {
        var offset = if (part.exists()) part.length() else 0L

        val builder = Request.Builder().url(sourceUrl)
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (offset > 0) builder.header("Range", "bytes=$offset-")

        client.newCall(builder.build()).execute().use { response ->
            when {
                response.code == 416 -> {
                    // Le fichier local est déjà complet.
                    return
                }

                !response.isSuccessful -> throw DownloadException(
                    "HTTP ${response.code}",
                    retryable = response.code >= 500 || response.code == 408 || response.code == 429,
                    retryAfterMs = parseRetryAfter(response.header("Retry-After")),
                )

                response.code == 200 && offset > 0 -> {
                    // Le serveur ignore Range : on ne peut pas ajouter au fichier existant.
                    part.delete()
                    offset = 0L
                }
            }

            val body = response.body ?: throw DownloadException("Réponse vide", retryable = true)
            val total = parseTotal(response.header("Content-Range"), offset, body.contentLength())
            var downloaded = offset

            onProgress(downloaded, total)

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            RandomAccessFile(part, "rw").use { out ->
                out.seek(offset)
                body.byteStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }

            if (total != null && downloaded < total) {
                throw DownloadException(
                    "Flux interrompu ($downloaded/$total octets)",
                    retryable = true,
                )
            }
        }
    }

    /**
     * `Retry-After` s'exprime en secondes ou en date HTTP ; on plafonne à une minute pour ne pas
     * immobiliser la file sur une valeur aberrante.
     */
    private fun parseRetryAfter(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        raw.trim().toLongOrNull()?.let { seconds -> return (seconds * 1000).coerceIn(0L, 60_000) }
        val date = runCatching {
            java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
                .parse(raw.trim())
                ?.time
        }.getOrNull() ?: return null
        return (date - System.currentTimeMillis()).coerceIn(0L, 60_000)
    }

    private fun parseTotal(contentRange: String?, offset: Long, contentLength: Long): Long? {
        // Content-Range: bytes 100-999/1000
        contentRange?.substringAfterLast('/')?.toLongOrNull()?.let { return it }
        if (contentLength <= 0) return null
        return offset + contentLength
    }

    private fun replaceAtomically(part: File, target: File) {
        if (target.exists() && !target.delete()) {
            throw DownloadException("Impossible de remplacer ${target.name}", retryable = false)
        }
        if (!part.renameTo(target)) {
            throw DownloadException("Impossible de finaliser ${target.name}", retryable = false)
        }
    }
}

// Plafond commun aux reprises : au-delà, mieux vaut rendre la main à l'utilisateur.
private const val MAX_BACKOFF_MS = 30_000L
