package io.selene.app.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
          <channel>
            <title>Mon podcast</title>
            <item>
              <title>Épisode 12 — les flux</title>
              <pubDate>Tue, 03 Jun 2025 10:00:00 +0000</pubDate>
              <itunes:duration>1:02:03</itunes:duration>
              <enclosure url="https://cdn.example.com/ep12.mp3" type="audio/mpeg" length="42000000"/>
            </item>
            <item>
              <title>Épisode 11 — la reprise</title>
              <pubDate>2025-05-27T09:30:00Z</pubDate>
              <itunes:duration>42:10</itunes:duration>
              <enclosure url="https://cdn.example.com/ep11.mp3" type="audio/mpeg"/>
            </item>
            <item>
              <title>Épisode sans fichier</title>
              <description>pas d'enclosure</description>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private val atom = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Vidéos libres</title>
          <entry>
            <title>Conférence</title>
            <published>2025-01-05T12:00:00Z</published>
            <link rel="enclosure" type="video/mp4" href="https://cdn.example.com/talk.mp4" length="90000000"/>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `un flux RSS donne son titre et ses episodes`() {
        val feed = FeedParser.parse(rss)
        assertEquals("Mon podcast", feed.title)
        assertEquals(2, feed.items.size) // l'item sans enclosure est ignoré
        assertEquals("Épisode 12 — les flux", feed.items[0].title)
        assertEquals("https://cdn.example.com/ep12.mp3", feed.items[0].url)
        assertEquals("audio/mpeg", feed.items[0].mimeType)
        assertEquals(42_000_000L, feed.items[0].sizeBytes)
    }

    @Test
    fun `la duree itunes est convertie en millisecondes`() {
        val feed = FeedParser.parse(rss)
        assertEquals(3_723_000L, feed.items[0].durationMs) // 1:02:03
        assertEquals(2_530_000L, feed.items[1].durationMs) // 42:10
    }

    @Test
    fun `les deux formats de date sont compris`() {
        val feed = FeedParser.parse(rss)
        assertEquals(1_748_944_800_000L, feed.items[0].publishedAt) // 2025-06-03T10:00:00Z
        assertEquals(1_748_338_200_000L, feed.items[1].publishedAt) // 2025-05-27T09:30:00Z
    }

    @Test
    fun `un flux Atom expose son enclosure`() {
        val feed = FeedParser.parse(atom)
        assertEquals("Vidéos libres", feed.title)
        assertEquals(1, feed.items.size)
        assertEquals("https://cdn.example.com/talk.mp4", feed.items.single().url)
        assertEquals("video/mp4", feed.items.single().mimeType)
        assertEquals(90_000_000L, feed.items.single().sizeBytes)
    }

    @Test(expected = InvalidFeedException::class)
    fun `un flux sans episode est signale`() {
        FeedParser.parse(
            """<rss version="2.0"><channel><title>Vide</title></channel></rss>""",
        )
    }

    @Test(expected = InvalidFeedException::class)
    fun `un document qui n est pas un flux est refuse`() {
        FeedParser.parse("""<html><body>page</body></html>""")
    }

    @Test
    fun `les durees s expriment en secondes ou en hh mm ss`() {
        assertEquals(3_723_000L, FeedParser.parseDurationMs("1:02:03"))
        assertEquals(2_530_000L, FeedParser.parseDurationMs("42:10"))
        assertEquals(90_000L, FeedParser.parseDurationMs("90"))
        assertNull(FeedParser.parseDurationMs(""))
        assertNull(FeedParser.parseDurationMs("n/a"))
    }

    @Test
    fun `une date illisible ne fait pas echouer le flux`() {
        assertNull(FeedParser.parseDateMs("hier"))
    }

    @Test
    fun `un fichier media direct est accepte comme repli`() {
        val feed = FeedParser.parse(
            """
            <rss version="2.0"><channel><title>T</title>
              <item><title>Direct</title><link href="https://cdn.example.com/fichier.m4a"/></item>
            </channel></rss>
            """.trimIndent(),
        )
        assertTrue(feed.items.single().url.endsWith("fichier.m4a"))
    }
}
