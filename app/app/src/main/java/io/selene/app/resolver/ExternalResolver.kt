package io.selene.app.resolver

import com.squareup.moshi.JsonClass
import io.selene.app.data.UserSettings
import io.selene.app.model.DownloadException
import io.selene.app.model.MediaKind
import io.selene.app.model.ResolvedMedia
import io.selene.app.model.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client d'un pont d'extraction **auto-hébergé par l'utilisateur**.
 *
 * Ce résolveur ne décode aucune page : il envoie l'adresse à un service que l'utilisateur fait
 * tourner lui-même (sa machine, son réseau ou un hôte de confiance) et récupère des URL de
 * fichiers qu'il transmet ensuite au moteur de téléchargement. Voir docs/EXTERNAL-RESOLVER.md.
 *
 * Trois garde-fous, tous dans le code :
 *  - désactivé par défaut, et `matches` renvoie faux tant que l'adresse est vide ;
 *  - jamais sollicité pour un fichier média direct, qui n'a besoin d'aucun pont ;
 *  - jeton obligatoire, transmis en `Authorization: Bearer`.
 */
@Singleton
class ExternalResolver @Inject constructor(
    private val client: OkHttpClient,
) : MediaResolver {

    override val id: String = "external"

    /** Dernier recours : après les résolveurs directs, HLS et DASH. */
    override val priority: Int = -100

    private val config = AtomicReference(ExternalConfig())

    /** Alimenté par l'application au démarrage et à chaque changement de réglage. */
    fun applySettings(settings: UserSettings) {
        config.set(
            ExternalConfig(
                enabled = settings.externalResolverEnabled,
                endpoint = settings.externalResolverEndpoint.trim().trimEnd('/'),
                token = settings.externalResolverToken,
            ),
        )
    }

    override fun matches(url: String): Boolean {
        val current = config.get()
        if (!current.enabled || current.endpoint.isEmpty() || current.token.isEmpty()) return false
        val cleaned = url.trim()
        if (!cleaned.startsWith("http://", true) && !cleaned.startsWith("https://", true)) return false
        // Un fichier média se télécharge sans pont : inutile d'exposer l'adresse à un service tiers.
        val path = cleaned.substringBefore('?').substringBefore('#').lowercase()
        return DIRECT_EXTENSIONS.none { path.endsWith(it) }
    }

    override suspend fun resolve(url: String): ResolvedMedia = withContext(Dispatchers.IO) {
        val current = config.get()
        if (!matches(url)) throw DownloadException("Pont externe non configuré", retryable = false)

        val payload = """{"url":${url.jsonQuoted()}}"""
        val request = Request.Builder()
            .url("${current.endpoint}/resolve")
            .header("Authorization", "Bearer ${current.token}")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw when (response.code) {
                    401, 403 -> DownloadException("Pont externe : jeton refusé", retryable = false)
                    422 -> DownloadException(
                        "Pont externe : adresse non prise en charge",
                        retryable = false,
                    )
                    503 -> DownloadException("Pont externe indisponible", retryable = true)
                    else -> DownloadException(
                        "Pont externe : HTTP ${response.code}",
                        retryable = response.code >= 500,
                    )
                }
            }
            response.body?.string().orEmpty()
        }

        val parsed = runCatching { responseAdapter.fromJson(body) }.getOrNull()
            ?: throw DownloadException("Pont externe : réponse JSON illisible", retryable = false)

        if (parsed.schema != SCHEMA) {
            throw DownloadException(
                "Pont externe : schéma ${parsed.schema ?: "absent"} au lieu de $SCHEMA",
                retryable = false,
            )
        }

        val variants = parsed.variants.orEmpty().mapNotNull { dto ->
            val target = dto.url ?: return@mapNotNull null
            Variant(
                id = dto.id ?: target,
                kind = when (dto.mime?.substringBefore('/')?.lowercase()) {
                    "audio" -> MediaKind.AUDIO
                    else -> MediaKind.VIDEO
                },
                container = dto.container
                    ?: target.substringBefore('?').substringAfterLast('.', "bin"),
                qualityLabel = dto.label ?: dto.height?.let { "${it}p" },
                bitrateKbps = dto.bitrate?.let { (it / 1000).toInt() },
                audioLanguage = dto.language,
                width = dto.width,
                height = dto.height,
                sizeBytes = dto.size_bytes,
                url = target,
                headers = dto.headers.orEmpty(),
            )
        }

        if (variants.isEmpty()) {
            throw DownloadException("Pont externe : aucune variante exploitable", retryable = false)
        }

        ResolvedMedia(
            sourceId = id,
            canonicalUrl = url.trim(),
            title = parsed.title?.takeIf { it.isNotBlank() } ?: url.trim(),
            author = parsed.author,
            durationMs = parsed.duration_ms,
            thumbnailUrl = parsed.thumbnail_url,
            variants = variants,
            subtitles = parsed.subtitles.orEmpty().mapNotNull { sub ->
                val target = sub.url ?: return@mapNotNull null
                io.selene.app.model.SubtitleTrack(
                    language = sub.lang ?: "und",
                    label = sub.label ?: sub.lang ?: "Sous-titres",
                    url = target,
                )
            },
        )
    }

    private val responseAdapter = MOSHI.adapter(ExternalResponse::class.java)

    private data class ExternalConfig(
        val enabled: Boolean = false,
        val endpoint: String = "",
        val token: String = "",
    )

    private companion object {
        const val SCHEMA = "selene.external/1"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val MOSHI: com.squareup.moshi.Moshi =
            com.squareup.moshi.Moshi.Builder()
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()

        val DIRECT_EXTENSIONS = listOf(
            ".mp4", ".m4v", ".webm", ".mkv", ".mov", ".avi",
            ".m4a", ".mp3", ".flac", ".ogg", ".opus", ".wav",
            ".m3u8", ".mpd", ".ts", ".vtt", ".srt",
        )
    }
}

/** Échappement minimal pour construire le corps de la requête sans dépendre d'un adaptateur. */
internal fun String.jsonQuoted(): String = buildString {
    append('"')
    for (char in this@jsonQuoted) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
        }
    }
    append('"')
}

// --- DTO du contrat `selene.external/1` -------------------------------------------------

@JsonClass(generateAdapter = false)
data class ExternalResponse(
    val schema: String? = null,
    val title: String? = null,
    val author: String? = null,
    val kind: String? = null,
    val duration_ms: Long? = null,
    val thumbnail_url: String? = null,
    val variants: List<ExternalVariant>? = null,
    val subtitles: List<ExternalSubtitle>? = null,
)

@JsonClass(generateAdapter = false)
data class ExternalVariant(
    val id: String? = null,
    val label: String? = null,
    val container: String? = null,
    val mime: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Long? = null,
    val size_bytes: Long? = null,
    val url: String? = null,
    val language: String? = null,
    val headers: Map<String, String>? = null,
)

@JsonClass(generateAdapter = false)
data class ExternalSubtitle(
    val lang: String? = null,
    val label: String? = null,
    val format: String? = null,
    val url: String? = null,
)
