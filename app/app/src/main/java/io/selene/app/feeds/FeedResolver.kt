package io.selene.app.feeds

import io.selene.app.model.DownloadException
import io.selene.app.model.MediaKind
import io.selene.app.model.ResolvedMedia
import io.selene.app.model.Variant
import io.selene.app.resolver.MediaResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résolveur de flux RSS / Atom : un podcast devient une liste d'épisodes téléchargeables.
 *
 * Choix assumé : les épisodes sont exposés comme des **variantes**, parce que toute l'interface
 * est construite autour de ce modèle (un média, plusieurs variantes, un choix, un téléchargement).
 * Le libellé de qualité porte le titre de l'épisode. Un écran dédié aux épisodes serait plus
 * juste ergonomiquement — c'est la prochaine étape, pas un oubli.
 */
@Singleton
class FeedResolver @Inject constructor(
    private val client: OkHttpClient,
) : MediaResolver {

    override val id: String = "feed"

    /** Sous HLS et DASH : un flux ne se confond pas avec un manifeste. */
    override val priority: Int = 70

    override fun matches(url: String): Boolean {
        val cleaned = url.trim().lowercase()
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) return false
        val path = cleaned.substringBefore('?').substringBefore('#')
        return path.endsWith(".rss") || path.endsWith(".atom") ||
            path.endsWith("feed") || path.endsWith("feed.xml") ||
            path.endsWith("rss.xml") || path.endsWith("atom.xml")
    }

    override suspend fun resolve(url: String): ResolvedMedia = withContext(Dispatchers.IO) {
        val cleaned = url.trim()
        val request = Request.Builder().url(cleaned).get().build()

        val xml = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException(
                    "HTTP ${response.code} sur le flux",
                    retryable = response.code >= 500 || response.code == 408 || response.code == 429,
                )
            }
            response.body?.string().orEmpty()
        }

        val feed = runCatching { FeedParser.parse(xml) }.getOrElse { error ->
            throw DownloadException(
                error.message ?: "Flux illisible",
                retryable = false,
                cause = error,
            )
        }

        val variants = feed.items.mapIndexed { index, item ->
            val kind = when {
                item.mimeType?.startsWith("audio/") == true -> MediaKind.AUDIO
                item.mimeType?.startsWith("video/") == true -> MediaKind.VIDEO
                else -> MediaKind.AUDIO
            }
            Variant(
                id = "feed:$index:${item.url.hashCode()}",
                kind = kind,
                container = item.url.substringBefore('?').substringAfterLast('.', "mp3").lowercase(),
                qualityLabel = item.title,
                sizeBytes = item.sizeBytes,
                url = item.url,
            )
        }

        ResolvedMedia(
            sourceId = id,
            canonicalUrl = cleaned,
            title = feed.title,
            variants = variants,
        )
    }
}
