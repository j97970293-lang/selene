package io.selene.app.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM du parseur DASH : XML en dur, aucun réseau, aucun appareil.
 * Les cas couverts sont ceux qui cassent en vrai : héritage des attributs, `SegmentTimeline`
 * avec répétition, gabarit numérique à zéro-padding, DRM, et fichier unique sans segmentation.
 */
class DashManifestParserTest {

    private val manifestUrl = "https://cdn.example.com/vod/manifest.mpd"

    private fun mpd(body: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" mediaPresentationDuration="$body"
    """.trimIndent()

    @Test
    fun `une duree ISO 8601 est convertie en millisecondes`() {
        assertEquals(3_723_500L, DashManifestParser.parseIsoDuration("PT1H2M3.5S"))
        assertEquals(600_000L, DashManifestParser.parseIsoDuration("PT10M"))
        assertEquals(2_000L, DashManifestParser.parseIsoDuration("PT2S"))
        assertNull(DashManifestParser.parseIsoDuration(null))
        assertNull(DashManifestParser.parseIsoDuration("n'importe quoi"))
    }

    @Test
    fun `un SegmentTemplate a duree fixe produit le bon nombre de segments`() {
        val xml = """
            <MPD mediaPresentationDuration="PT30S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1000" duration="10000" startNumber="1"
                                   initialization="init-${'$'}RepresentationID${'$'}.mp4"
                                   media="chunk-${'$'}Number${'$'}.m4s"/>
                  <Representation id="v1" bandwidth="2500000" width="1920" height="1080"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val manifest = DashManifestParser.parse(xml, manifestUrl)
        val rep = manifest.representations.single()

        assertEquals(3, rep.segments.size)
        assertEquals("https://cdn.example.com/vod/chunk-1.m4s", rep.segments[0])
        assertEquals("https://cdn.example.com/vod/chunk-3.m4s", rep.segments[2])
        assertEquals(1080, rep.height)
        assertEquals(2_500_000L, rep.bandwidthBps)
    }

    @Test
    fun `un SegmentTimeline avec repetition est developpe`() {
        val xml = """
            <MPD mediaPresentationDuration="PT20S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate media="seg-${'$'}Number${'$'}.m4s" startNumber="10">
                    <SegmentTimeline>
                      <S t="0" d="4000" r="2"/>
                      <S d="4000" r="1"/>
                    </SegmentTimeline>
                  </SegmentTemplate>
                  <Representation id="v1" bandwidth="800000" width="1280" height="720"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val rep = DashManifestParser.parse(xml, manifestUrl).representations.single()
        // r=2 → 3 occurrences, puis r=1 → 2 occurrences : 5 segments, numérotés à partir de 10.
        assertEquals(5, rep.segments.size)
        assertEquals("https://cdn.example.com/vod/seg-10.m4s", rep.segments.first())
        assertEquals("https://cdn.example.com/vod/seg-14.m4s", rep.segments.last())
    }

    @Test
    fun `un gabarit a zero-padding est respecte`() {
        val xml = """
            <MPD mediaPresentationDuration="PT20S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1000" duration="5000"
                                   media="seg-${'$'}Number%05d${'$'}.m4s"/>
                  <Representation id="v1" bandwidth="500000"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val rep = DashManifestParser.parse(xml, manifestUrl).representations.single()
        assertEquals("https://cdn.example.com/vod/seg-00001.m4s", rep.segments.first())
        assertEquals(4, rep.segments.size)
    }

    @Test
    fun `le SegmentTemplate herite de l AdaptationSet`() {
        val xml = """
            <MPD mediaPresentationDuration="PT20S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1000" duration="5000" media="s-${'$'}Number${'$'}.m4s"/>
                  <Representation id="v1" bandwidth="500000"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val rep = DashManifestParser.parse(xml, manifestUrl).representations.single()
        assertEquals(4, rep.segments.size)
    }

    @Test
    fun `un BaseURL unique donne un fichier direct sans segment`() {
        val xml = """
            <MPD mediaPresentationDuration="PT10S">
              <Period>
                <AdaptationSet mimeType="audio/mp4" lang="fr">
                  <Representation id="a1" bandwidth="128000">
                    <BaseURL>audio-fr.m4a</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val rep = DashManifestParser.parse(xml, manifestUrl).representations.single()
        assertEquals("https://cdn.example.com/vod/audio-fr.m4a", rep.singleFile)
        assertTrue(rep.segments.isEmpty())
        assertEquals("fr", rep.language)
        assertEquals("audio/mp4", rep.mimeType)
    }

    @Test(expected = EncryptedPlaylistException::class)
    fun `une ContentProtection est refusee explicitement`() {
        val xml = """
            <MPD mediaPresentationDuration="PT10S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"/>
                  <Representation id="v1" bandwidth="500000">
                    <BaseURL>v.mp4</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        DashManifestParser.parse(xml, manifestUrl)
    }

    @Test(expected = InvalidManifestException::class)
    fun `un XML invalide est signale`() {
        DashManifestParser.parse("<MPD><Period>", manifestUrl)
    }

    @Test(expected = InvalidManifestException::class)
    fun `une racine inattendue est signalee`() {
        DashManifestParser.parse("<html><body>page</body></html>", manifestUrl)
    }

    @Test(expected = InvalidManifestException::class)
    fun `un manifeste sans representation est signale`() {
        DashManifestParser.parse("""<MPD mediaPresentationDuration="PT10S"><Period/></MPD>""", manifestUrl)
    }

    @Test
    fun `plusieurs pistes video et audio sont toutes listees`() {
        val xml = """
            <MPD mediaPresentationDuration="PT20S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1000" duration="10000" media="v${'$'}RepresentationID${'$'}-${'$'}Number${'$'}.m4s"/>
                  <Representation id="1080" bandwidth="4000000" width="1920" height="1080"/>
                  <Representation id="720" bandwidth="1500000" width="1280" height="720"/>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4" lang="fr">
                  <SegmentTemplate timescale="1000" duration="10000" media="a-${'$'}Number${'$'}.m4s"/>
                  <Representation id="a-fr" bandwidth="128000"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val manifest = DashManifestParser.parse(xml, manifestUrl)
        assertEquals(3, manifest.representations.size)
        assertEquals(20_000L, manifest.durationMs)
        assertEquals(listOf("1080", "720", "a-fr"), manifest.representations.map { it.id })
        assertEquals(
            "https://cdn.example.com/vod/v1080-1.m4s",
            manifest.representations.first().segments.first(),
        )
    }
}
