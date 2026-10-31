package io.selene.app.resolver

import io.selene.app.model.DownloadException
import io.selene.app.model.MediaKind
import io.selene.app.model.ResolvedMedia
import io.selene.app.model.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résolveur HLS : `https://…/master.m3u8` → une variante par débit annoncé.
 *
 * Le fichier produit est un `.ts` (les segments mis bout à bout), pas un `.mp4` : le remux
 * demande un moteur multimédia, et mentir sur l'extension produirait des fichiers que le
 * lecteur système refuse d'ouvrir. Mieux vaut un TS lisible partout qu'un faux MP4.
 */
@Singleton
class HlsResolver @Inject constructor(
    private val client: OkHttpClient,
) : MediaResolver {

    override val id: String = "hls"

    /** Au-dessus du résolveur direct, qui accepterait n'importe quelle URL `http(s)`. */
    override val priority: Int = 100

    override fun matches(url: String): Boolean {
        val cleaned = url.trim().lowercase()
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) return false
        return cleaned.substringBefore('?').substringBefore('#').endsWith(".m3u8")
    }

    override suspend fun resolve(url: String): ResolvedMedia = withContext(Dispatchers.IO) {
        val cleaned = url.trim()
        val playlist = fetchPlaylist(cleaned)
        val title = cleaned.substringAfterLast('/')
            .substringBefore('?')
            .substringBeforeLast('.')
            .ifEmpty { "Flux HLS" }

        if (!playlist.isMaster) {
            // Manifeste média : un seul fichier, découpé en segments.
            return@withContext ResolvedMedia(
                sourceId = id,
                canonicalUrl = cleaned,
                title = title,
                variants = listOf(
                    Variant(
                        id = "$id:${playlist.segmentCount}",
                        kind = MediaKind.VIDEO,
                        // fMP4 (#EXT-X-MAP) = vrai MP4 ; sinon TS, et on l'annonce tel quel.
                        container = if (playlist.mapUri != null) "mp4" else "ts",
                        qualityLabel = null,
                        sizeBytes = null,
                        url = cleaned,
                    ),
                ),
            )
        }

        val variants = playlist.renditions
            .sortedByDescending { it.height ?: 0 }
            .mapIndexed { index, rendition ->
                Variant(
                    id = "$id:$index:${rendition.height ?: 0}",
                    kind = MediaKind.VIDEO,
                    container = "ts",
                    qualityLabel = rendition.height?.let { "${it}p" },
                    bitrateKbps = rendition.bandwidthBps?.let { (it / 1000).toInt() },
                    width = rendition.width,
                    height = rendition.height,
                    audioLanguage = rendition.audioGroup?.let { playlist.audioLanguages[it] },
                    sizeBytes = null,
                    url = rendition.uri,
                )
            }
            .distinctBy { it.qualityLabel to it.bitrateKbps }

        if (variants.isEmpty()) {
            throw DownloadException("Manifeste HLS illisible : aucune variante annoncée", retryable = false)
        }

        ResolvedMedia(
            sourceId = id,
            canonicalUrl = cleaned,
            title = title,
            variants = variants,
        )
    }

    private fun fetchPlaylist(playlistUrl: String): HlsPlaylist {
        val request = Request.Builder().url(playlistUrl).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException(
                    "HTTP ${response.code} lors de la lecture du manifeste",
                    retryable = response.code >= 500 || response.code == 408 || response.code == 429,
                )
            }

            val body = response.body?.string().orEmpty()
            if (!body.contains("#EXTM3U")) {
                throw DownloadException(
                    "Ce fichier n'est pas un manifeste HLS (#EXTM3U absent)",
                    retryable = false,
                )
            }

            val base = response.request.url.toString()
            val playlist = PlaylistParser.parse(body, base)

            // Manifeste maître sans variante mais avec segments : cas dégénéré, on le traite
            // comme un manifeste média.
            return if (playlist.isMaster || playlist.segmentCount > 0) {
                playlist
            } else {
                throw DownloadException("Manifeste HLS vide", retryable = false)
            }
        }
    }
}
