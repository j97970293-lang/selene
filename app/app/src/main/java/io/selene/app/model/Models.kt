package io.selene.app.model

/** Nature d'une variante téléchargeable. */
enum class MediaKind { VIDEO, AUDIO }

/** Une déclinaison téléchargeable d'un média. */
data class Variant(
    val id: String,
    val kind: MediaKind,
    val container: String,
    val qualityLabel: String? = null,
    val bitrateKbps: Int? = null,
    val audioLanguage: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) {
    /** Libellé compact utilisé dans l'UI, sans dépendre d'une ressource. */
    val displayLabel: String
        get() = buildList {
            add(kind.name.lowercase().replaceFirstChar { it.uppercase() })
            qualityLabel?.let { add(it) }
            if (kind == MediaKind.AUDIO) bitrateKbps?.let { add("$it kbps") }
            add(container.uppercase())
            audioLanguage?.let { add(it) }
        }.joinToString(" · ")
}

data class SubtitleTrack(
    val language: String,
    val label: String,
    val url: String,
)

/** Résultat d'une résolution : ce que l'app sait du média avant de télécharger. */
data class ResolvedMedia(
    val sourceId: String,
    val canonicalUrl: String,
    val title: String,
    val author: String? = null,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null,
    val variants: List<Variant>,
    val subtitles: List<SubtitleTrack> = emptyList(),
)

/** Cycle de vie d'un téléchargement. */
enum class TaskState { QUEUED, RUNNING, PAUSED, FAILED, DONE, CANCELLED }

data class DownloadTask(
    val id: String,
    val variantId: String,
    val title: String,
    val state: TaskState,
    val bytesDownloaded: Long = 0,
    val bytesTotal: Long? = null,
    val attempt: Int = 0,
    val lastError: String? = null,
    val localUri: String? = null,
) {
    val progress: Float?
        get() = bytesTotal?.takeIf { it > 0 }?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
}

/** Erreur de téléchargement classée : conditionne la stratégie de reprise. */
class DownloadException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause)
