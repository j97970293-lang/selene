package io.selene.app.downloading

import io.selene.app.model.DownloadException
import io.selene.app.resolver.HlsPlaylist
import io.selene.app.resolver.PlaylistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Téléchargement d'un flux HLS : lecture du manifeste, puis mise bout à bout des segments.
 *
 * Le fichier final porte l'extension `.ts`. Un remux vers MP4 viendra avec Media3 Transformer ;
 * en attendant, l'extension annoncée est celle du contenu réel.
 */
class HlsDownloader(
    private val client: OkHttpClient,
    private val segmentDownloader: SegmentDownloader,
) {

    suspend fun download(
        playlistUrl: String,
        target: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val playlist = fetchMediaPlaylist(playlistUrl, headers)
        if (playlist.segments.isEmpty()) {
            throw DownloadException("Manifeste HLS sans segment", retryable = false)
        }
        segmentDownloader.download(withInitSegment(playlist), target, headers, onProgress)
        target
    }

    companion object {
        /**
         * Place le segment `#EXT-X-MAP` en tête. Sans lui, un flux fMP4 produit un fichier sans
         * en-tête `moov` : illisible, alors que tous les octets ont bien été téléchargés.
         */
        fun withInitSegment(playlist: HlsPlaylist): List<String> =
            listOfNotNull(playlist.mapUri) + playlist.segments
    }

    private fun fetchMediaPlaylist(playlistUrl: String, headers: Map<String, String>): HlsPlaylist {
        var current = playlistUrl

        // Un manifeste maître pointe vers des manifestes média : on descend en prenant le
        // meilleur débit annoncé.
        repeat(3) {
            val builder = Request.Builder().url(current).get()
            headers.forEach { (name, value) -> builder.header(name, value) }

            val body = client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadException(
                        "HTTP ${response.code} sur le manifeste HLS",
                        retryable = response.code >= 500 || response.code == 408 || response.code == 429,
                    )
                }
                response.body?.string().orEmpty()
            }

            val playlist = PlaylistParser.parse(body, current)
            if (!playlist.isMaster) return playlist

            val best = playlist.renditions.maxByOrNull { it.bandwidthBps ?: 0L }
                ?: throw DownloadException("Manifeste maître HLS vide", retryable = false)
            current = best.uri
        }

        throw DownloadException("Manifeste HLS imbriqué trop profondément", retryable = false)
    }
}
