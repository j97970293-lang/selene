package io.selene.app.downloading

import io.selene.app.model.DownloadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Téléchargement d'une liste de segments mise bout à bout, avec reprise **par segment**.
 *
 * Partagé par HLS et DASH : la mécanique est identique, seuls le manifeste et la génération des
 * URL diffèrent. L'index du dernier segment écrit vit dans `<cible>.seg`, donc la reprise survit
 * à un redémarrage du téléphone.
 *
 * Chaque segment est confié au [DownloadEngine] commun : reprise par octet, tentatives multiples
 * et backoff sont ainsi écrits une seule fois.
 */
class SegmentDownloader(
    private val engine: DownloadEngine,
) {

    suspend fun download(
        segments: List<String>,
        target: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) throw DownloadException("Aucun segment à télécharger", retryable = false)

        target.parentFile?.mkdirs()
        val part = File("${target.path}.part")
        val stateFile = File("${target.path}.seg")
        val tempSegment = File("${target.path}.segment.tmp")

        var completed = stateFile.takeIf { it.exists() }
            ?.readText()?.trim()?.toIntOrNull() ?: 0
        if (completed > segments.size) completed = 0
        if (completed == 0) {
            part.delete()
            onProgress(0L, null)
        }

        var written = part.takeIf { it.exists() }?.length() ?: 0L

        for (index in completed until segments.size) {
            coroutineContext.ensureActive()

            val segment = engine.download(segments[index], tempSegment, headers)
            part.appendBytes(segment.readBytes())
            segment.delete()
            written = part.length()

            stateFile.writeText((index + 1).toString())
            onProgress(written, null)
        }

        tempSegment.delete()
        stateFile.delete()

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        target
    }
}
