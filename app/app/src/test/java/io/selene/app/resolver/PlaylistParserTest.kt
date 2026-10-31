package io.selene.app.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM du parseur HLS : aucun appareil, aucun réseau, aucune donnée mobile.
 * Fixtures réelles (master multi-débits avec pistes audio, playlists média, URLs relatives).
 */
class PlaylistParserTest {

    private val master = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",LANGUAGE="fr",NAME="Français, VF",URI="audio/fr.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",LANGUAGE="en",NAME="English",URI="audio/en.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=4200000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",AUDIO="aud"
        1080/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=1280x720,AUDIO="aud"
        https://cdn.example.com/720/index.m3u8
    """.trimIndent()

    private val media = """
        #EXTM3U
        #EXT-X-TARGETDURATION:10
        #EXT-X-KEY:METHOD=NONE
        #EXTINF:9.999,
        seg0.ts
        #EXTINF:10.0,
        sub/seg1.ts
        #EXTINF:4.5,
        ../seg2.ts
        #EXT-X-ENDLIST
    """.trimIndent()

    @Test
    fun `un manifeste maitre est reconnu et ses variantes sont resolues`() {
        val playlist = PlaylistParser.parse(master, "https://cdn.example.com/vod/master.m3u8?token=abc")

        assertTrue(playlist.isMaster)
        assertEquals(2, playlist.renditions.size)
        assertEquals(1080, playlist.renditions[0].height)
        assertEquals(4_200_000L, playlist.renditions[0].bandwidthBps)
        // URL relative résolue contre l'URL du manifeste, query comprise.
        assertEquals(
            "https://cdn.example.com/vod/1080/index.m3u8",
            playlist.renditions[0].uri,
        )
        assertEquals(
            "https://cdn.example.com/720/index.m3u8",
            playlist.renditions[1].uri,
        )
    }

    @Test
    fun `le groupe audio retient la premiere piste, pas la derniere`() {
        val playlist = PlaylistParser.parse(master, "https://cdn.example.com/vod/master.m3u8")
        assertEquals("fr", playlist.audioLanguages["aud"])
    }

    @Test
    fun `une piste audio marquee DEFAULT l emporte`() {
        val withDefault = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",LANGUAGE="fr",NAME="VF"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",LANGUAGE="en",NAME="VO",DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=800000,AUDIO="aud"
            index.m3u8
        """.trimIndent()

        val playlist = PlaylistParser.parse(withDefault, "https://cdn.example.com/m.m3u8")
        assertEquals("en", playlist.audioLanguages["aud"])
    }

    @Test
    fun `un manifeste media liste ses segments en URL absolues`() {
        val playlist = PlaylistParser.parse(media, "https://cdn.example.com/vod/1080/index.m3u8")

        assertFalse(playlist.isMaster)
        assertEquals(3, playlist.segmentCount)
        assertEquals("https://cdn.example.com/vod/1080/seg0.ts", playlist.segments[0])
        assertEquals("https://cdn.example.com/vod/1080/sub/seg1.ts", playlist.segments[1])
        assertEquals("https://cdn.example.com/vod/seg2.ts", playlist.segments[2])
    }

    @Test
    fun `la duree cumulee provient des EXTINF`() {
        val playlist = PlaylistParser.parse(media, "https://cdn.example.com/vod/1080/index.m3u8")
        assertEquals(24_499L, playlist.durationMs)
    }

    @Test(expected = EncryptedPlaylistException::class)
    fun `un flux chiffre AES-128 est refuse explicitement`() {
        val encrypted = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/k1"
            #EXTINF:2,
            a.ts
        """.trimIndent()
        PlaylistParser.parse(encrypted, "https://cdn.example.com/flux.m3u8")
    }

    @Test
    fun `METHOD=NONE n est pas un chiffrement`() {
        val playlist = PlaylistParser.parse(media, "https://cdn.example.com/vod/1080/index.m3u8")
        assertEquals(3, playlist.segmentCount)
    }

    @Test
    fun `les attributs entre guillemets peuvent contenir des virgules`() {
        val attrs = PlaylistParser.parseAttributes("""TYPE=AUDIO,NAME="Français, VF",LANGUAGE="fr"""")
        assertEquals("Français, VF", attrs["NAME"])
        assertEquals("fr", attrs["LANGUAGE"])
        assertEquals(3, attrs.size)
    }

    @Test
    fun `une playlist vide ne produit ni variante ni segment`() {
        val playlist = PlaylistParser.parse("#EXTM3U\n#EXT-X-ENDLIST", "https://cdn.example.com/x.m3u8")
        assertFalse(playlist.isMaster)
        assertEquals(0, playlist.segmentCount)
    }

    @Test
    fun `un segment d initialisation EXT-X-MAP est reconnu et resolu`() {
        val fmp4 = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg1.m4s
            #EXTINF:6.0,
            seg2.m4s
        """.trimIndent()

        val playlist = PlaylistParser.parse(fmp4, "https://cdn.example.com/vod/index.m3u8")
        assertEquals("https://cdn.example.com/vod/init.mp4", playlist.mapUri)
        assertEquals(2, playlist.segmentCount)
    }

    @Test
    fun `sans EXT-X-MAP le champ reste vide`() {
        val playlist = PlaylistParser.parse(media, "https://cdn.example.com/vod/1080/index.m3u8")
        assertNull(playlist.mapUri)
    }

    @Test
    fun `une reference absolue n est pas alteree`() {
        assertEquals(
            "https://autre.example.com/a/b.ts",
            PlaylistParser.absolute("https://cdn.example.com/x/index.m3u8", "https://autre.example.com/a/b.ts"),
        )
    }

    @Test
    fun `une url de base invalide ne fait pas planter le parseur`() {
        assertNull(PlaylistParser.absolute("pas une url", "seg.ts"))
    }
}
