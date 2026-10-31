package io.selene.app.resolver

import java.net.URI

/** Une déclinaison annoncée par un manifeste maître HLS. */
data class HlsRendition(
    val uri: String,
    val bandwidthBps: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val audioGroup: String? = null,
    val language: String? = null,
    val name: String? = null,
)

/** Contenu d'un manifeste HLS, maître ou média. */
data class HlsPlaylist(
    val isMaster: Boolean,
    val renditions: List<HlsRendition> = emptyList(),
    val segments: List<String> = emptyList(),
    val durationMs: Long = 0,
    /** GROUP-ID → code de langue, pour rattacher une piste audio à sa variante. */
    val audioLanguages: Map<String, String> = emptyMap(),
    /** `#EXT-X-MAP` : segment d'initialisation d'un flux fMP4, à écrire avant les segments. */
    val mapUri: String? = null,
) {
    val segmentCount: Int get() = segments.size
}

/**
 * Analyseur de manifeste HLS (`.m3u8`), sans réseau ni dépendance Android : il est testable
 * sur la JVM, ce qui est la seule façon sérieuse de vérifier un parseur de playlist.
 *
 * Supporté : `#EXT-X-STREAM-INF`, `#EXT-X-MEDIA` (audio), `#EXTINF`, `#EXT-X-ENDLIST`,
 * URLs relatives, attributs entre guillemets contenant des virgules.
 * Non supporté volontairement : `#EXT-X-KEY` (chiffrement) et `#EXT-X-MAP` avec clé — le
 * téléchargement de flux chiffrés n'est pas dans le périmètre du projet (voir BOUNDARY.md).
 */
object PlaylistParser {

    fun parse(text: String, baseUrl: String): HlsPlaylist {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#EXTM3U") }
            .toList()

        val audioLanguages = linkedMapOf<String, String>()
        // Une piste marquée DEFAULT=YES l'emporte ; sinon la première rencontrée est retenue.
        // Sans ce garde-fou, le dernier groupe audio écrasait le premier et le français devenait
        // anglais — bug attrapé sur fixture avant livraison.
        val defaultAudioGroups = mutableSetOf<String>()
        val renditions = mutableListOf<HlsRendition>()
        val segments = mutableListOf<String>()
        var durationSeconds = 0.0
        var pendingDuration = 0.0
        var pendingStreamAttrs: Map<String, String>? = null
        var mapUri: String? = null
        var encrypted = false

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> {
                    pendingStreamAttrs = parseAttributes(line.substringAfter(':'))
                }

                line.startsWith("#EXT-X-MEDIA:") -> {
                    val attrs = parseAttributes(line.substringAfter(':'))
                    if (attrs["TYPE"].equals("AUDIO", ignoreCase = true)) {
                        val group = attrs["GROUP-ID"].orEmpty()
                        val lang = attrs["LANGUAGE"] ?: attrs["NAME"]
                        val isDefault = attrs["DEFAULT"].equals("YES", ignoreCase = true)
                        if (group.isNotEmpty() && lang != null) {
                            val shouldWrite = when {
                                isDefault && group !in defaultAudioGroups -> true
                                group !in audioLanguages -> true
                                else -> false
                            }
                            if (shouldWrite) {
                                audioLanguages[group] = lang
                                if (isDefault) defaultAudioGroups += group
                            }
                        }
                    }
                }

                line.startsWith("#EXT-X-KEY:") -> {
                    // METHOD=NONE est autorisé ; toute autre méthode signifie un flux protégé.
                    val method = parseAttributes(line.substringAfter(':'))["METHOD"].orEmpty()
                    if (!method.equals("NONE", ignoreCase = true)) encrypted = true
                }

                line.startsWith("#EXT-X-MAP:") -> {
                    val attrs = parseAttributes(line.substringAfter(':'))
                    mapUri = attrs["URI"]?.let { absolute(baseUrl, it) }
                }

                line.startsWith("#EXTINF:") -> {
                    pendingDuration = line.substringAfter(':')
                        .substringBefore(',')
                        .trim()
                        .toDoubleOrNull() ?: 0.0
                }

                line.startsWith("#") -> Unit // autre balise, ignorée

                else -> {
                    // Ligne d'URI : soit une variante du manifeste maître, soit un segment.
                    val absolute = absolute(baseUrl, line) ?: line
                    val attrs = pendingStreamAttrs
                    if (attrs != null) {
                        val resolution = attrs["RESOLUTION"].orEmpty()
                        val width = resolution.substringBefore('x').toIntOrNull()
                        val height = resolution.substringAfter('x', missingDelimiterValue = "").toIntOrNull()
                        renditions += HlsRendition(
                            uri = absolute,
                            bandwidthBps = attrs["BANDWIDTH"]?.toLongOrNull(),
                            width = width,
                            height = height,
                            audioGroup = attrs["AUDIO"],
                        )
                        pendingStreamAttrs = null
                    } else {
                        segments += absolute
                        durationSeconds += pendingDuration
                        pendingDuration = 0.0
                    }
                }
            }
        }

        if (encrypted) {
            throw EncryptedPlaylistException()
        }

        return HlsPlaylist(
            isMaster = renditions.isNotEmpty(),
            renditions = renditions,
            segments = segments,
            durationMs = (durationSeconds * 1000).toLong(),
            audioLanguages = audioLanguages,
            mapUri = mapUri,
        )
    }

    /** Résout une URI relative contre l'URL du manifeste. Renvoie null si irrésoluble. */
    fun absolute(baseUrl: String, reference: String): String? = runCatching {
        URI(baseUrl).resolve(reference.trim()).toString()
    }.getOrNull()

    /**
     * Découpe une liste d'attributs HLS (`A=1,B="x,y",C=`) en respectant les guillemets.
     * Une virgule entre guillemets ne sépare pas deux attributs — piège classique.
     */
    fun parseAttributes(raw: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val current = StringBuilder()
        val parts = mutableListOf<String>()
        var inQuotes = false

        for (char in raw) {
            when {
                char == '"' -> { inQuotes = !inQuotes; current.append(char) }
                char == ',' && !inQuotes -> { parts += current.toString(); current.clear() }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()

        for (part in parts) {
            val key = part.substringBefore('=', missingDelimiterValue = "").trim()
            if (key.isEmpty()) continue
            val value = part.substringAfter('=', missingDelimiterValue = "")
                .trim()
                .removeSurrounding("\"")
            result[key] = value
        }
        return result
    }
}

/** Le manifeste est chiffré (DRM ou AES-128) : hors périmètre, message explicite. */
class EncryptedPlaylistException :
    IllegalArgumentException("Ce flux est protégé par un chiffrement : téléchargement refusé.")
