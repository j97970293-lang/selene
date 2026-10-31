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
 * Résolveur DASH : `https://…/manifest.mpd` → une variante par piste annoncée.
 *
 * Particularité assumée du format : dans la majorité des manifestes, la vidéo et l'audio sont
 * **deux pistes séparées**. Sans multiplexeur, on ne peut pas produire un MP4 complet en une
 * passe, donc les deux pistes sont proposées distinctement plutôt que de livrer silencieusement
 * une vidéo muette. Le remux viendra avec Media3 Transformer ; d'ici là, l'utilisateur voit ce
 * qu'il télécharge.
 */
@Singleton
class DashResolver @Inject constructor(
    private val client: OkHttpClient,
) : MediaResolver {

    override val id: String = "dash"

    /** Juste sous HLS, au-dessus du résolveur direct. */
    override val priority: Int = 90

    override fun matches(url: String): Boolean {
        val cleaned = url.trim().lowercase()
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) return false
        return cleaned.substringBefore('?').substringBefore('#').endsWith(".mpd")
    }

    override suspend fun resolve(url: String): ResolvedMedia = withContext(Dispatchers.IO) {
        val cleaned = url.trim()
        val manifest = fetchManifest(cleaned)
        val title = cleaned.substringAfterLast('/')
            .substringBefore('?')
            .substringBeforeLast('.')
            .ifEmpty { "Flux DASH" }

        val variants = manifest.representations
            .filter { it.segments.isNotEmpty() || it.singleFile != null }
            .sortedWith(
                compareByDescending<DashRepresentation> { it.height ?: 0 }
                    .thenByDescending { it.bandwidthBps ?: 0L },
            )
            .map { representation ->
                val isAudio = representation.mimeType?.startsWith("audio/") == true
                Variant(
                    id = "$id:${representation.id}",
                    kind = if (isAudio) MediaKind.AUDIO else MediaKind.VIDEO,
                    container = if (isAudio) "m4a" else "mp4",
                    qualityLabel = representation.height?.let { "${it}p" },
                    bitrateKbps = representation.bandwidthBps?.let { (it / 1000).toInt() },
                    audioLanguage = representation.language,
                    width = representation.width,
                    height = representation.height,
                    sizeBytes = null,
                    // L'URL persistée reste celle du manifeste : la sélection de piste voyage
                    // dans l'identifiant (`dash:<repId>`), ce qui garde une reprise possible même
                    // si les URL signées du manifeste expirent.
                    url = cleaned,
                )
            }
            .distinctBy { it.kind to (it.qualityLabel ?: it.bitrateKbps) }

        if (variants.isEmpty()) {
            throw DownloadException("Manifeste DASH sans piste exploitable", retryable = false)
        }

        ResolvedMedia(
            sourceId = id,
            canonicalUrl = cleaned,
            title = title,
            durationMs = manifest.durationMs,
            variants = variants,
        )
    }

    private fun fetchManifest(manifestUrl: String): DashManifest {
        val request = Request.Builder().url(manifestUrl).get().build()

        val xml = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException(
                    "HTTP ${response.code} lors de la lecture du manifeste DASH",
                    retryable = response.code >= 500 || response.code == 408 || response.code == 429,
                )
            }
            response.body?.string().orEmpty()
        }

        if (!xml.contains("<MPD")) {
            throw DownloadException("Ce fichier n'est pas un manifeste DASH", retryable = false)
        }

        return DashManifestParser.parse(xml, manifestUrl)
    }
}
