package io.selene.app.resolver

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.mapper.VideoFormat
import io.selene.app.model.DownloadException
import io.selene.app.model.MediaKind
import io.selene.app.model.ResolvedMedia
import io.selene.app.model.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Résout les pages prises en charge par yt-dlp. Les URL de fichiers et de manifestes restent
 * traitées par les résolveurs Selene natifs afin de conserver leur reprise par octet/segment.
 */
class YtDlpResolver @Inject constructor() : MediaResolver {
    override val id: String = "yt-dlp"
    override val priority: Int = 50

    override fun matches(url: String): Boolean {
        val normalized = url.trim()
        if (!normalized.startsWith("http://", ignoreCase = true) &&
            !normalized.startsWith("https://", ignoreCase = true)
        ) return false
        val path = normalized.substringBefore('?').substringBefore('#').lowercase()
        return !MEDIA_EXTENSIONS.any { path.endsWith(it) }
    }

    override suspend fun resolve(url: String): ResolvedMedia = withContext(Dispatchers.IO) {
        val info = try {
            YoutubeDL.getInfo(url)
        } catch (error: Exception) {
            throw DownloadException(
                error.message?.takeIf { it.isNotBlank() } ?: "yt-dlp n'a pas pu analyser cette adresse",
                retryable = false,
                cause = error,
            )
        }

        val variants = info.formats.orEmpty().mapNotNull(::toVariant).distinctBy { it.id }
        if (variants.isEmpty()) {
            throw DownloadException("yt-dlp n'a trouvé aucun format téléchargeable", retryable = false)
        }

        ResolvedMedia(
            sourceId = id,
            canonicalUrl = info.webpageUrl ?: url,
            title = info.title?.takeIf { it.isNotBlank() } ?: "Téléchargement yt-dlp",
            author = info.uploader,
            durationMs = info.duration.takeIf { it > 0 }?.toLong()?.times(1000),
            thumbnailUrl = info.thumbnail,
            variants = variants.sortedWith(
                compareByDescending<Variant> { it.kind == MediaKind.VIDEO }
                    .thenByDescending { it.height ?: 0 }
                    .thenByDescending { it.bitrateKbps ?: 0 },
            ),
        )
    }

    private fun toVariant(format: VideoFormat): Variant? {
        val formatId = format.formatId ?: return null
        val url = format.url ?: format.manifestUrl ?: return null
        val hasVideo = !format.vcodec.isNullOrBlank() && format.vcodec != "none"
        val protocol = format.protocol(url)
        val container = when {
            protocol.startsWith("m3u8") -> "ts"
            protocol.startsWith("http_dash_segments") -> "mp4"
            else -> format.ext ?: url.substringBefore('?').substringAfterLast('.', "bin")
        }
        return Variant(
            id = "yt-dlp:$formatId",
            kind = if (hasVideo) MediaKind.VIDEO else MediaKind.AUDIO,
            container = container,
            qualityLabel = format.height.takeIf { it > 0 }?.let { "${it}p" }
                ?: format.formatNote
                ?: formatId,
            bitrateKbps = (format.tbr.takeIf { it > 0 } ?: format.abr.takeIf { it > 0 }),
            width = format.width.takeIf { it > 0 },
            height = format.height.takeIf { it > 0 },
            sizeBytes = format.fileSize.takeIf { it > 0 } ?: format.fileSizeApproximate.takeIf { it > 0 },
            url = url,
            headers = format.httpHeaders.orEmpty(),
        )
    }

    private fun VideoFormat.protocol(url: String): String = when {
        manifestUrl != null && manifestUrl!!.substringBefore('?').endsWith(".m3u8", true) -> "m3u8"
        manifestUrl != null && manifestUrl!!.substringBefore('?').endsWith(".mpd", true) -> "http_dash_segments"
        url.substringBefore('?').endsWith(".m3u8", true) -> "m3u8"
        else -> "http"
    }

    private companion object {
        val MEDIA_EXTENSIONS = setOf(
            ".3gp", ".aac", ".avi", ".flac", ".flv", ".m4a", ".m4v", ".mkv", ".mov",
            ".mp3", ".mp4", ".mpeg", ".mpg", ".oga", ".ogg", ".opus", ".ts", ".wav",
            ".webm", ".m3u8", ".mpd",
        )
    }
}
