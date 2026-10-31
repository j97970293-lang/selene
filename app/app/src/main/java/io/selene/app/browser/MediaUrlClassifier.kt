package io.selene.app.browser

/** Ce qu'un navigateur peut observer d'une page, sans rien décoder. */
enum class DetectedKind {
    /** Manifeste HLS ou DASH : à confier aux résolveurs natifs. */
    MANIFEST,
    VIDEO,
    AUDIO,
    SUBTITLE,

    /** Segment isolé (`.ts`, `.m4s`) : la page assemble le média en JavaScript. */
    SEGMENT,

    /** `blob:` / `data:` : le média n'existe pas à une adresse — indétectable. */
    OPAQUE,
}

data class DetectedMedia(val url: String, val kind: DetectedKind) {
    /** Clé de dédoublonnage : même adresse sans le fragment. */
    val key: String get() = MediaUrlClassifier.keyOf(url)
}

/**
 * Classification des adresses observées par le navigateur intégré.
 *
 * Kotlin pur : la règle de décision est testable sans WebView, et le navigateur
 * ne fait plus que l'appeler. Une adresse non reconnue n'est pas retenue : mieux
 * vaut une liste courte et juste qu'une liste de bruit.
 */
object MediaUrlClassifier {

    private val EXTENSIONS = mapOf(
        "m3u8" to DetectedKind.MANIFEST,
        "mpd" to DetectedKind.MANIFEST,
        "mp4" to DetectedKind.VIDEO,
        "m4v" to DetectedKind.VIDEO,
        "webm" to DetectedKind.VIDEO,
        "mkv" to DetectedKind.VIDEO,
        "mov" to DetectedKind.VIDEO,
        "avi" to DetectedKind.VIDEO,
        "3gp" to DetectedKind.VIDEO,
        "flv" to DetectedKind.VIDEO,
        "m4a" to DetectedKind.AUDIO,
        "mp3" to DetectedKind.AUDIO,
        "opus" to DetectedKind.AUDIO,
        "ogg" to DetectedKind.AUDIO,
        "oga" to DetectedKind.AUDIO,
        "flac" to DetectedKind.AUDIO,
        "wav" to DetectedKind.AUDIO,
        "aac" to DetectedKind.AUDIO,
        "vtt" to DetectedKind.SUBTITLE,
        "srt" to DetectedKind.SUBTITLE,
        "ass" to DetectedKind.SUBTITLE,
        "ts" to DetectedKind.SEGMENT,
        "m4s" to DetectedKind.SEGMENT,
        "cmfv" to DetectedKind.SEGMENT,
        "cmfa" to DetectedKind.SEGMENT,
    )

    /**
     * Extension → type. `null` signifie « pas un média » : la page charge une
     * image, un script, une police ou une API.
     */
    fun classify(url: String, mime: String? = null): DetectedKind? {
        val cleaned = url.trim()
        if (cleaned.isEmpty()) return null

        // Un média assemblé à la volée n'a pas d'adresse récupérable : on le signale
        // pour ne pas laisser croire que la liste est incomplète par accident.
        if (cleaned.startsWith("blob:", true) || cleaned.startsWith("data:", true)) {
            return if (mime?.startsWith("media/") == true || mime == null) DetectedKind.OPAQUE else null
        }

        mime?.lowercase()?.let { type ->
            when {
                type.startsWith("application/vnd.apple.mpegurl") -> return DetectedKind.MANIFEST
                type.startsWith("application/x-mpegurl") -> return DetectedKind.MANIFEST
                type.startsWith("application/dash+xml") -> return DetectedKind.MANIFEST
                type.startsWith("video/") -> return DetectedKind.VIDEO
                type.startsWith("audio/") -> return DetectedKind.AUDIO
                type.startsWith("text/vtt") -> return DetectedKind.SUBTITLE
            }
        }

        val extension = extensionOf(cleaned) ?: return null
        return EXTENSIONS[extension]
    }

    /** `null` si l'adresse n'apprend rien ; sinon l'entrée à ajouter à la liste. */
    fun detect(url: String, mime: String? = null): DetectedMedia? {
        val cleaned = url.trim()
        if (cleaned.isEmpty()) return null
        if (!cleaned.startsWith("http://", true) && !cleaned.startsWith("https://", true) &&
            !cleaned.startsWith("blob:", true) && !cleaned.startsWith("data:", true)
        ) {
            return null
        }
        val kind = classify(cleaned, mime) ?: return null
        return DetectedMedia(url = cleaned, kind = kind)
    }

    /** Clé stable : le fragment (`#t=30`) ne change pas le fichier visé. */
    fun keyOf(url: String): String = url.trim().substringBefore('#')

    private fun extensionOf(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        val last = path.substringAfterLast('/', path)
        if (!last.contains('.')) return null
        val extension = last.substringAfterLast('.').lowercase()
        if (extension.isEmpty() || extension.length > 5) return null
        if (extension.any { !it.isLetterOrDigit() }) return null
        return extension
    }
}
