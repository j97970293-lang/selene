package io.selene.app.downloading

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.selene.app.model.DownloadException
import io.selene.app.model.MediaKind
import io.selene.app.model.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/** Télécharge un format yt-dlp, y compris les formats séparés fusionnés par FFmpeg. */
class YtDlpDownloader {
    suspend fun download(
        pageUrl: String,
        variant: Variant,
        target: File,
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val formatId = variant.id.removePrefix("yt-dlp:")
        val formatSelector = when (variant.kind) {
            MediaKind.VIDEO -> "$formatId+bestaudio/$formatId"
            MediaKind.AUDIO -> formatId
        }
        val request = YoutubeDLRequest(pageUrl)
            .addOption("--format", formatSelector)
            .addOption("--output", target.absolutePath)
            .addOption("--no-playlist")
            .addOption("--no-part")
            .addOption("--no-mtime")
            .addOption("--no-overwrites")
            .addOption("--newline")
            .addOption("--merge-output-format", target.extension.ifBlank { "mp4" })

        val total = variant.sizeBytes
        try {
            YoutubeDL.execute(request) { percent, _, _ ->
                val downloaded = total?.let { (it * percent.coerceIn(0f, 100f) / 100f).toLong() } ?: 0L
                runBlocking { onProgress(downloaded, total) }
            }
        } catch (error: Exception) {
            throw DownloadException(
                error.message?.takeIf { it.isNotBlank() } ?: "yt-dlp n'a pas pu télécharger ce format",
                retryable = true,
                cause = error,
            )
        }

        if (target.isFile) return@withContext target
        val candidates = target.parentFile
            ?.listFiles { file ->
                file.isFile && file.nameWithoutExtension == target.nameWithoutExtension &&
                    !file.name.endsWith(".part")
            }
            .orEmpty()
        val produced = candidates.maxByOrNull { it.lastModified() }
            ?: throw DownloadException("yt-dlp a terminé sans produire de fichier", retryable = false)
        if (!produced.renameTo(target) && !target.isFile) {
            throw DownloadException("Impossible de finaliser ${target.name}", retryable = false)
        }
        target
    }
}
