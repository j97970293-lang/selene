package io.selene.app.feeds

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Une entrée de flux : un épisode téléchargeable, ou au moins une piste. */
data class FeedItem(
    val title: String,
    val url: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val publishedAt: Long?,
    val durationMs: Long?,
)

data class Feed(
    val title: String,
    val items: List<FeedItem>,
)

/**
 * Analyse de flux RSS 2.0 et Atom — sans réseau, donc testable sur JVM.
 *
 * Ce qui est lu : `channel/item` et `entry`, l'URL venant de `enclosure@url` (RSS) ou d'un
 * `link rel="enclosure"` (Atom), avec repli sur un lien direct de média. Les formats de date
 * courants (RFC 822 et ISO 8601) sont convertis en millisecondes.
 */
object FeedParser {

    fun parse(xml: String): Feed {
        val document = runCatching {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }.newDocumentBuilder()
                .apply {
                    setErrorHandler(object : org.xml.sax.ErrorHandler {
                        override fun warning(e: org.xml.sax.SAXParseException) = Unit
                        override fun error(e: org.xml.sax.SAXParseException) = throw e
                        override fun fatalError(e: org.xml.sax.SAXParseException) = throw e
                    })
                }
                .parse(ByteArrayInputStream(xml.toByteArray()))
        }.getOrNull() ?: throw InvalidFeedException("Flux illisible : XML invalide")

        val root = document.documentElement
            ?: throw InvalidFeedException("Flux vide")

        val channelTitle = when (root.tagName.lowercase()) {
            "rss" -> root.children("channel").firstOrNull()?.childText("title")
            "feed" -> root.childText("title")
            else -> throw InvalidFeedException("Ce document n'est pas un flux (racine ${root.tagName})")
        }.orEmpty().ifBlank { "Flux" }

        val itemTags = if (root.tagName.lowercase() == "rss") "item" else "entry"
        val items = root.children("channel").firstOrNull()?.children(itemTags)
            ?: root.children(itemTags)

        val parsed = items.mapNotNull { element ->
            val title = element.childText("title")?.trim().orEmpty().ifBlank { "Sans titre" }
            val url = enclosureUrl(element) ?: return@mapNotNull null
            val mime = element.children("enclosure").firstOrNull()?.attr("type")
                ?: element.children("link")
                    .firstOrNull { it.attr("rel").equals("enclosure", true) }
                    ?.attr("type")
            FeedItem(
                title = title,
                url = url,
                mimeType = mime,
                // La taille est portée par `enclosure@length` en RSS, `link@length` en Atom.
                sizeBytes = element.children("enclosure").firstOrNull()?.attr("length")?.toLongOrNull()
                    ?: element.children("link")
                        .firstOrNull { it.attr("rel").equals("enclosure", true) }
                        ?.attr("length")
                        ?.toLongOrNull(),
                publishedAt = parseDateMs(
                    element.childText("pubDate") ?: element.childText("published"),
                ),
                durationMs = element.childText("duration")?.let(::parseDurationMs),
            )
        }

        if (parsed.isEmpty()) {
            throw InvalidFeedException("Aucun épisode téléchargeable dans ce flux")
        }

        return Feed(title = channelTitle, items = parsed)
    }

    private fun enclosureUrl(element: Element): String? {
        element.children("enclosure").firstOrNull()?.attr("url")?.let { return it }
        element.children("link")
            .firstOrNull { it.attr("rel").equals("enclosure", true) }
            ?.attr("href")
            ?.let { return it }
        // Repli : un lien media:content ou un href unique pointant vers un fichier média.
        element.children("content").firstOrNull()?.attr("url")?.let { return it }
        return element.children("link")
            .firstOrNull { it.attr("href")?.substringBefore('?')?.contains('.') == true }
            ?.attr("href")
    }

    /** `1:02:03`, `62:03` ou `3723` (secondes) → millisecondes. */
    fun parseDurationMs(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.all { it.isDigit() }) return text.toLongOrNull()?.times(1000)
        val parts = text.split(':').mapNotNull { it.toDoubleOrNull() }
        if (parts.isEmpty()) return null
        val seconds = when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> parts[0]
        }
        return (seconds * 1000).toLong()
    }

    /** RFC 822 (`Tue, 03 Jun 2025 10:00:00 +0000`) et ISO 8601 (`2025-06-03T10:00:00Z`). */
    fun parseDateMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd",
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                java.text.SimpleDateFormat(pattern, java.util.Locale.ENGLISH).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.parse(raw.trim())
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { it as Element }
            .filter { it.tagName.substringAfter(':').equals(tag, ignoreCase = true) }

    private fun Element.childText(tag: String): String? =
        children(tag).firstOrNull()?.textContent?.trim()

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }
}

class InvalidFeedException(message: String) : IllegalArgumentException(message)
