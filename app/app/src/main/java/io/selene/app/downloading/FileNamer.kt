package io.selene.app.downloading

import java.io.File
import java.text.Normalizer

/**
 * Construit un nom de fichier sûr pour le stockage local.
 *
 * Règles : translittération des accents, suppression des caractères interdits ou de contrôle,
 * troncature à 120 caractères sans couper un mot si possible, et suffixe de collision « (2) ».
 */
object FileNamer {

    private const val MAX_LENGTH = 120
    private val forbidden = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val whitespace = Regex("""\s+""")

    fun sanitize(rawTitle: String, extension: String): String {
        val base = Normalizer.normalize(rawTitle, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(forbidden, " ")
            .replace(whitespace, " ")
            .trim()
            .trim('.', ' ')
            .ifEmpty { "media" }

        val safeExtension = extension.trim().trimStart('.').lowercase()
        val suffix = if (safeExtension.isEmpty()) "" else ".$safeExtension"
        val room = (MAX_LENGTH - suffix.length).coerceAtLeast(16)

        val truncated = if (base.length <= room) {
            base
        } else {
            val cut = base.take(room)
            val lastSpace = cut.lastIndexOf(' ')
            // On ne coupe au mot que si l'on ne perd pas plus de 20 % du contenu utile.
            if (lastSpace > room * 0.8) cut.take(lastSpace) else cut
        }.trim()

        return truncated + suffix
    }

    /** Renvoie un fichier libre dans [directory] en évitant d'écraser un fichier existant. */
    fun uniqueFile(directory: File, desiredName: String): File {
        val first = File(directory, desiredName)
        if (!first.exists()) return first

        val stem = desiredName.substringBeforeLast('.', desiredName)
        val extension = desiredName.substringAfterLast('.', "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"

        var index = 2
        while (true) {
            val candidate = File(directory, "$stem ($index)$suffix")
            if (!candidate.exists()) return candidate
            index++
            if (index > 500) return File(directory, "$stem-${System.currentTimeMillis()}$suffix")
        }
    }
}
