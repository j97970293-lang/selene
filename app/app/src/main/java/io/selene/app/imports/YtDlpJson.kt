package io.selene.app.imports

import com.squareup.moshi.JsonClass
import io.selene.app.model.DownloadException
import io.selene.app.model.MediaKind
import io.selene.app.model.ResolvedMedia
import io.selene.app.model.SubtitleTrack
import io.selene.app.model.Variant

/**
 * Import d'une sortie `yt-dlp -J` (JSON) déjà produite par l'utilisateur.
 *
 * Principe : l'extraction a lieu **dehors**. L'utilisateur lance `yt-dlp -J <adresse>` avec
 * l'outil qu'il a choisi, sur la machine qu'il veut, et dépose le fichier JSON dans Selene ;
 * Selene en tire des variantes et télécharge avec son propre moteur (reprise, file, coffre).
 *
 * Selene n'exécute pas yt-dlp, ne l'embarque pas et n'écrit pas de décodeur de page : il lit un
 * format de sortie public et documenté. C'est le même contrat que le pont HTTP, sans serveur.
 */
object YtDlpJson {

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(YtDlpInfo::class.java)

    fun parse(json: String, sourceUrl: String = ""): ResolvedMedia {
        val info = runCatching { adapter.fromJson(json) }.getOrNull()
            ?: throw DownloadException("Fichier JSON illisible", retryable = false)

        val variants = info.formats.orEmpty().mapNotNull { format ->
            val url = format.url ?: format.manifest_url ?: return@mapNotNull null
            val protocol = format.protocol.orEmpty()
            // `vcodec: "none"` (ou absent) = pas de flux vidéo : c'est une piste audio.
            val hasVideo = !format.vcodec.isNullOrBlank() && format.vcodec != "none"
            val isAudioOnly = !hasVideo
            val container = when {
                protocol.startsWith("m3u8") -> "ts"
                protocol.startsWith("http_dash_segments") -> "mp4"
                else -> format.ext ?: url.substringBefore('?').substringAfterLast('.', "bin")
            }
            Variant(
                id = "ytdlp:${format.format_id ?: url}",
                kind = if (isAudioOnly) MediaKind.AUDIO else MediaKind.VIDEO,
                container = container,
                qualityLabel = format.height?.let { "${it}p" }
                    ?: format.format_note
                    ?: format.format_id,
                bitrateKbps = format.tbr?.let { it.toInt() },
                audioLanguage = format.language,
                width = format.width,
                height = format.height,
                sizeBytes = format.filesize ?: format.filesize_approx,
                url = url,
                headers = format.http_headers.orEmpty(),
            )
        }.distinctBy { it.id }

        if (variants.isEmpty()) {
            throw DownloadException(
                "Ce JSON ne contient aucune adresse téléchargeable (lancez `yt-dlp -J`, pas `-j`)",
                retryable = false,
            )
        }

        val subtitles = info.subtitles.orEmpty().flatMap { (lang, tracks) ->
            tracks.mapNotNull { track ->
                val url = track.url ?: return@mapNotNull null
                SubtitleTrack(
                    language = lang,
                    label = track.name ?: lang,
                    url = url,
                )
            }
        }

        return ResolvedMedia(
            sourceId = "ytdlp-import",
            canonicalUrl = info.webpage_url ?: sourceUrl,
            title = info.title ?: "Import yt-dlp",
            author = info.uploader ?: info.channel,
            durationMs = info.duration?.let { (it * 1000).toLong() },
            thumbnailUrl = info.thumbnail,
            variants = variants.sortedWith(
                compareByDescending<Variant> { it.kind == MediaKind.VIDEO }
                    .thenByDescending { it.height ?: 0 },
            ),
            subtitles = subtitles,
        )
    }
}

// --- Sous-ensemble public de la sortie `yt-dlp -J` -------------------------------------

@JsonClass(generateAdapter = false)
data class YtDlpInfo(
    val id: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val channel: String? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    val webpage_url: String? = null,
    val formats: List<YtDlpFormat>? = null,
    val subtitles: Map<String, List<YtDlpSubtitle>>? = null,
)

@JsonClass(generateAdapter = false)
data class YtDlpFormat(
    val format_id: String? = null,
    val format_note: String? = null,
    val url: String? = null,
    val manifest_url: String? = null,
    val ext: String? = null,
    val protocol: String? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val tbr: Double? = null,
    val filesize: Long? = null,
    val filesize_approx: Long? = null,
    val language: String? = null,
    val http_headers: Map<String, String>? = null,
)

@JsonClass(generateAdapter = false)
data class YtDlpSubtitle(
    val url: String? = null,
    val name: String? = null,
    val ext: String? = null,
)
