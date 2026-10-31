package io.selene.app.resolver

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

/** Une piste audio ou vidéo annoncée par un manifeste DASH. */
data class DashRepresentation(
    val id: String,
    val mimeType: String?,
    val codecs: String?,
    val bandwidthBps: Long?,
    val width: Int?,
    val height: Int?,
    val language: String?,
    /** Segments dans l'ordre ; vide si la représentation est un fichier unique. */
    val segments: List<String>,
    /** Segment d'initialisation (`moov` d'un fMP4) ; à écrire avant les segments. */
    val initSegment: String?,
    /** Fichier unique, quand le manifeste expose directement une URL exploitable. */
    val singleFile: String? = null,
)

data class DashManifest(
    val durationMs: Long?,
    val representations: List<DashRepresentation>,
)

/**
 * Analyseur de manifeste DASH (`.mpd`).
 *
 * Périmètre : `SegmentTemplate` (avec `SegmentTimeline` ou `duration` fixe), `SegmentList`,
 * `BaseURL` en fichier unique, héritage des attributs `Period` → `AdaptationSet` → `Representation`.
 * Hors périmètre, refusé explicitement : `ContentProtection` (DRM), `SegmentBase` avec index
 * chiffré.
 *
 * Aucun réseau ici : le parseur reçoit le XML et l'URL du manifeste, donc il se teste sur JVM.
 */
object DashManifestParser {

    fun parse(xml: String, manifestUrl: String): DashManifest {
        val document = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                // Aucune entité externe : un manifeste hostile ne doit pas lire le disque.
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            factory.newDocumentBuilder()
                .apply {
                    setErrorHandler(object : org.xml.sax.ErrorHandler {
                        override fun warning(e: org.xml.sax.SAXParseException) = Unit
                        override fun error(e: org.xml.sax.SAXParseException) = throw e
                        override fun fatalError(e: org.xml.sax.SAXParseException) = throw e
                    })
                }
                .parse(ByteArrayInputStream(xml.toByteArray()))
        }.getOrNull() ?: throw InvalidManifestException("Manifeste DASH illisible : XML invalide")

        val root = document.documentElement
            ?: throw InvalidManifestException("Manifeste DASH vide")
        if (root.tagName != "MPD") {
            throw InvalidManifestException("Ce fichier n'est pas un manifeste DASH (racine ${root.tagName})")
        }

        if (root.children("Period").any { period ->
                period.children("AdaptationSet").any { it.children("ContentProtection").isNotEmpty() }
            }
        ) {
            throw EncryptedPlaylistException()
        }

        val mpdDuration = parseIsoDuration(root.attr("mediaPresentationDuration"))
        val baseUrl = urlOf(manifestUrl, root.children("BaseURL").firstOrNull()?.textContent)

        val representations = mutableListOf<DashRepresentation>()

        for (period in root.children("Period")) {
            val periodDuration = parseIsoDuration(period.attr("duration")) ?: mpdDuration
            for (adaptation in period.children("AdaptationSet")) {
                val adaptationMime = adaptation.attr("mimeType")
                val adaptationLang = adaptation.attr("lang")
                for (representation in adaptation.children("Representation")) {
                    val built = buildRepresentation(
                        representation = representation,
                        adaptation = adaptation,
                        baseUrl = baseUrl,
                        fallbackMime = adaptationMime,
                        fallbackLang = adaptationLang,
                        durationMs = periodDuration,
                    ) ?: continue
                    representations += built
                }
            }
        }

        if (representations.isEmpty()) {
            throw InvalidManifestException("Manifeste DASH sans représentation exploitable")
        }

        return DashManifest(durationMs = mpdDuration, representations = representations)
    }

    private fun buildRepresentation(
        representation: Element,
        adaptation: Element,
        baseUrl: String,
        fallbackMime: String?,
        fallbackLang: String?,
        durationMs: Long?,
    ): DashRepresentation? {
        val ownBaseUrl = representation.children("BaseURL").firstOrNull()?.textContent
        val base = urlOf(baseUrl, ownBaseUrl)
        val repId = representation.attr("id") ?: base
        fun fill(pattern: String): String = pattern.replace("\$RepresentationID\$", repId)

        val mime = representation.attr("mimeType") ?: fallbackMime
        val language = representation.attr("lang") ?: fallbackLang

        // SegmentTemplate peut être défini au niveau Representation, AdaptationSet ou Period.
        val template = representation.children("SegmentTemplate").firstOrNull()
            ?: adaptation.children("SegmentTemplate").firstOrNull()
            ?: adaptation.parentOrNull()?.children("SegmentTemplate")?.firstOrNull()

        val segmentList = representation.children("SegmentList").firstOrNull()
            ?: adaptation.children("SegmentList").firstOrNull()

        val init: String?
        val segments: List<String>

        when {
            segmentList != null -> {
                init = segmentList.children("Initialization").firstOrNull()
                    ?.attr("sourceURL")?.let { urlOf(base, fill(it)) }
                segments = segmentList.children("SegmentURL").mapNotNull { node ->
                    node.attr("media")?.let { urlOf(base, it) }
                }
            }

            template != null -> {
                init = template.attr("initialization")?.let { urlOf(base, fill(it)) }
                segments = expandTemplate(template, base, durationMs, repId)
            }

            else -> {
                // Pas de segmentation : l'URL de base désigne un fichier unique.
                init = null
                segments = emptyList()
            }
        }

        val single = if (segments.isEmpty()) base.takeIf { it.isNotBlank() && it != baseUrl } else null

        if (segments.isEmpty() && single == null) return null

        return DashRepresentation(
            id = repId,
            mimeType = mime,
            codecs = representation.attr("codecs"),
            bandwidthBps = representation.attr("bandwidth")?.toLongOrNull(),
            width = representation.attr("width")?.toIntOrNull(),
            height = representation.attr("height")?.toIntOrNull(),
            language = language,
            segments = segments,
            initSegment = init,
            singleFile = single,
        )
    }

    /**
     * Développe un `SegmentTemplate` en liste d'URL.
     *
     * Deux cas : `SegmentTimeline` (suite de `S` avec `d`, `r` et éventuellement `t`) ou
     * `duration` fixe, où le nombre de segments se déduit de la durée de la période.
     */
    fun expandTemplate(
        template: Element,
        baseUrl: String,
        durationMs: Long?,
        representationId: String = "",
    ): List<String> {
        val media = template.attr("media") ?: return emptyList()
        val startNumber = template.attr("startNumber")?.toIntOrNull() ?: 1
        val timescale = template.attr("timescale")?.toLongOrNull() ?: 1L
        val fixedDuration = template.attr("duration")?.toLongOrNull()

        val timeline = template.children("SegmentTimeline").firstOrNull()
        val numbers = mutableListOf<Long>()
        val times = mutableListOf<Long>()

        if (timeline != null) {
            var cursor = 0L
            for (s in timeline.children("S")) {
                val d = s.attr("d")?.toLongOrNull() ?: continue
                val t = s.attr("t")?.toLongOrNull()
                if (t != null) cursor = t
                val repeat = s.attr("r")?.toIntOrNull() ?: 0
                // r = -1 : jusqu'au prochain S/@t, ou jusqu'à la fin de la période.
                val count = if (repeat >= 0) {
                    repeat + 1
                } else {
                    val next = s.nextSiblingElement()?.attr("t")?.toLongOrNull()
                    when {
                        next != null -> ((next - cursor) / d).toInt().coerceAtLeast(1)
                        durationMs != null -> {
                            val budget = durationMs * timescale / 1000 - cursor
                            (budget / d).toInt().coerceAtLeast(1)
                        }
                        else -> 1
                    }
                }
                repeat(count) {
                    numbers += (startNumber + numbers.size).toLong()
                    times += cursor
                    cursor += d
                }
            }
        } else if (fixedDuration != null && fixedDuration > 0 && durationMs != null) {
            val segmentMs = fixedDuration * 1000 / timescale
            val count = ((durationMs + segmentMs - 1) / segmentMs).toInt().coerceAtLeast(1)
            repeat(count) { index ->
                numbers += (startNumber + index).toLong()
                times += fixedDuration * index
            }
        } else {
            return emptyList()
        }

        return numbers.mapIndexed { index, number ->
            urlOf(
                baseUrl,
                media
                    .replace("\$Number\$", number.toString())
                    .replace("\$Number%0${numberWidth(media)}d\$", number.toString().padStart(numberWidth(media), '0'))
                    .replace("\$Time\$", times[index].toString())
                    .replace("\$RepresentationID\$", representationId),
            ).orEmpty()
        }.filter { it.isNotEmpty() }
    }

    /** Largeur de remplissage d'un `$Number%05d$` éventuel. */
    private fun numberWidth(media: String): Int =
        Regex("\\\$Number%0(\\d+)d\\\$").find(media)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    /** `PT1H2M3.5S` → millisecondes. */
    fun parseIsoDuration(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val match = Regex(
            """^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?)?$""",
        ).find(value.trim()) ?: return null
        val (years, months, days, hours, minutes, seconds) = match.destructured
        val ms = (hours.toLongOrNull() ?: 0) * 3_600_000L +
            (minutes.toLongOrNull() ?: 0) * 60_000L +
            ((seconds.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        val daysPart = (days.toLongOrNull() ?: 0) * 86_400_000L +
            (months.toLongOrNull() ?: 0) * 2_592_000_000L + // mois moyen de 30 jours
            (years.toLongOrNull() ?: 0) * 31_536_000_000L
        return ms + daysPart
    }

    private fun urlOf(base: String, reference: String?): String {
        if (reference.isNullOrBlank()) return base
        val trimmed = reference.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return runCatching { URI(base).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }

    private fun Element.attr(name: String): String? =
        getAttribute(name).takeIf { it.isNotEmpty() }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE && (it as Element).tagName == tag }
            .map { it as Element }

    private fun Element.parentOrNull(): Element? = parentNode as? Element

    private fun Element.nextSiblingElement(): Element? {
        var node = nextSibling
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) return node as Element
            node = node.nextSibling
        }
        return null
    }
}

class InvalidManifestException(message: String) : IllegalArgumentException(message)
