package io.selene.app.resolver

import io.selene.app.model.DownloadException
import io.selene.app.model.UnsupportedContentException
import io.selene.app.model.MediaKind
import io.selene.app.model.ResolvedMedia
import io.selene.app.model.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résolveur v1 : une URL http(s) qui pointe directement sur un fichier média.
 *
 * Volontairement limité. Une page HTML quelconque n'est pas résolue : sans analyse de la page,
 * on retomberait sur du scraping, ce que ce projet ne fait pas (RULES.md).
 */
@Singleton
class DirectUrlResolver @Inject constructor(
    private val client: OkHttpClient,
) : MediaResolver {

    override val id: String = "direct"

    override val priority: Int = 0

    override fun matches(url: String): Boolean =
        url.trim().let { it.startsWith("http://", true) || it.startsWith("https://", true) }

    override suspend fun resolve(url: String): ResolvedMedia = withContext(Dispatchers.IO) {
        val cleaned = url.trim()
        val request = Request.Builder().url(cleaned).head().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException(
                    "HTTP ${response.code} lors de l'analyse",
                    retryable = response.code >= 500 || response.code == 408 || response.code == 429,
                )
            }

            val mime = response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                .orEmpty()

            val kind = when {
                mime.startsWith("video/") -> MediaKind.VIDEO
                mime.startsWith("audio/") -> MediaKind.AUDIO
                mime == "application/octet-stream" || mime.isEmpty() -> MediaKind.VIDEO
                else -> throw UnsupportedContentException(
                    "Type de contenu non pris en charge : ${mime.ifEmpty { "inconnu" }}",
                )
            }

            val sizeBytes = response.header("Content-Length")?.toLongOrNull()
            val fileName = cleaned.substringAfterLast('/').substringBefore('?').ifEmpty { "media" }
            val container = fileName.substringAfterLast('.', missingDelimiterValue = "")
                .ifEmpty { if (kind == MediaKind.AUDIO) "m4a" else "mp4" }
                .lowercase()

            val labels = response.header("Content-Disposition")
                ?.split(';')
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith("filename=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim('"', ' ')
                ?.takeIf { it.isNotEmpty() }

            ResolvedMedia(
                sourceId = id,
                canonicalUrl = cleaned,
                title = (labels ?: fileName).substringBeforeLast('.'),
                author = null,
                durationMs = null,
                thumbnailUrl = null,
                variants = listOf(
                    Variant(
                        id = UUID.randomUUID().toString(),
                        kind = kind,
                        container = container,
                        sizeBytes = sizeBytes,
                        url = cleaned,
                    ),
                ),
            )
        }
    }
}
