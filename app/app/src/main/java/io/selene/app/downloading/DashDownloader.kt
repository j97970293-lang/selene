package io.selene.app.downloading

import io.selene.app.model.DownloadException
import io.selene.app.resolver.DashManifestParser
import io.selene.app.resolver.DashRepresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Téléchargement d'une piste DASH : segment d'initialisation, puis segments dans l'ordre.
 *
 * La piste est identifiée par `representationId`, transporté dans l'identifiant de variante
 * (`dash:<repId>`). Conséquence utile : l'URL stockée reste celle du manifeste, et la reprise
 * fonctionne même après expiration des URL signées qu'il contenait.
 */
class DashDownloader(
    private val client: OkHttpClient,
    private val engine: DownloadEngine,
    private val segmentDownloader: SegmentDownloader,
) {

    suspend fun download(
        manifestUrl: String,
        representationId: String,
        target: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val manifest = fetchManifest(manifestUrl, headers)

        val representation = manifest.representations.firstOrNull { it.id == representationId }
            ?: manifest.representations.firstOrNull { it.singleFile != null }
            ?: throw DownloadException(
                "Piste « $representationId » absente du manifeste",
                retryable = false,
            )

        val single = representation.singleFile
        if (single != null && representation.segments.isEmpty()) {
            engine.download(single, target, headers, onProgress)
            return@withContext target
        }

        segmentDownloader.download(
            segments = orderedSegments(representation),
            target = target,
            headers = headers,
            onProgress = onProgress,
        )
        target
    }

    /** Le segment d'initialisation (`moov`) doit précéder les médias, sinon le fichier est illisible. */
    private fun orderedSegments(representation: DashRepresentation): List<String> {
        val init = representation.initSegment
        return if (init != null) listOf(init) + representation.segments else representation.segments
    }

    private fun fetchManifest(manifestUrl: String, headers: Map<String, String>) = run {
        val builder = Request.Builder().url(manifestUrl).get()
        headers.forEach { (name, value) -> builder.header(name, value) }

        val xml = client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException(
                    "HTTP ${response.code} sur le manifeste DASH",
                    retryable = response.code >= 500 || response.code == 408 || response.code == 429,
                )
            }
            response.body?.string().orEmpty()
        }
        DashManifestParser.parse(xml, manifestUrl)
    }
}
