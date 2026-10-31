package io.selene.app.downloading

/** Destination logique d'un fichier exporté hors de l'application. */
enum class MediaFamily { VIDEO, AUDIO, DOCUMENT }

/**
 * Choix de la collection MediaStore et du type MIME à partir de l'extension.
 *
 * Kotlin pur, sans Android : c'est la seule partie de l'export qui peut être
 * vérifiée sans appareil, et c'est celle où une erreur se voit tout de suite
 * (un MP3 rangé dans « Films » n'apparaît pas dans le lecteur de musique).
 */
object MediaMime {

    /** Extension sans le point, en minuscules ; chaîne vide s'il n'y en a pas. */
    fun extensionOf(nameOrUrl: String): String {
        val path = nameOrUrl.substringBefore('#').substringBefore('?')
        val candidate = path.substringAfterLast('/', path).substringAfterLast('.', "")
        if (candidate.isEmpty() || candidate.length > 5) return ""
        if (candidate.any { !it.isLetterOrDigit() }) return ""
        // « 1.2.3 » ou « ep.42 » : un nombre n'est pas une extension de média.
        if (candidate.all { it.isDigit() }) return ""
        return candidate.lowercase()
    }

    fun familyFor(extension: String): MediaFamily = when (extension.lowercase().trimStart('.')) {
        "m4a", "mp3", "opus", "ogg", "oga", "flac", "wav", "aac", "weba", "mka" -> MediaFamily.AUDIO
        "mp4", "m4v", "webm", "mkv", "mov", "avi", "3gp", "3gpp", "ts", "mts", "flv" -> MediaFamily.VIDEO
        else -> MediaFamily.DOCUMENT
    }

    fun mimeFor(extension: String): String = when (extension.lowercase().trimStart('.')) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "3gp", "3gpp" -> "video/3gpp"
        "ts", "mts" -> "video/mp2t"
        "flv" -> "video/x-flv"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus", "ogg", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        else -> "application/octet-stream"
    }

    /**
     * Chemin relatif MediaStore. `Download/` est la seule collection garantie
     * sur toutes les versions ; `Movies/` et `Music/` sont indexées par les
     * galeries et les lecteurs, ce qui est le but de l'export.
     */
    fun relativePath(family: MediaFamily): String = when (family) {
        MediaFamily.VIDEO -> "Movies/Selene"
        MediaFamily.AUDIO -> "Music/Selene"
        MediaFamily.DOCUMENT -> "Download/Selene"
    }
}
