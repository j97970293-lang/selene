package io.selene.app.imports

import io.selene.app.model.DownloadException
import io.selene.app.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM de l'import d'une sortie `yt-dlp -J` : aucun réseau, aucune exécution de yt-dlp.
 * La fixture reprend la forme réelle du JSON (formats muxés, vidéo seule, audio seul, HLS,
 * sous-titres, en-têtes HTTP).
 */
class YtDlpJsonTest {

    private val fixture = """
        {
          "id": "abc123",
          "title": "Exemple de vidéo",
          "uploader": "Une chaîne",
          "duration": 754.0,
          "thumbnail": "https://i.example.com/thumb.jpg",
          "webpage_url": "https://exemple.tld/watch?v=abc123",
          "formats": [
            {"format_id":"137","ext":"mp4","vcodec":"avc1.640028","acodec":"none",
             "width":1920,"height":1080,"tbr":4200.5,"filesize":395000000,
             "url":"https://cdn.example.com/1080.mp4",
             "http_headers":{"Referer":"https://exemple.tld/"}},
            {"format_id":"22","ext":"mp4","vcodec":"avc1.64001F","acodec":"mp4a.40.2",
             "width":1280,"height":720,"tbr":1500.0,"filesize_approx":120000000,
             "url":"https://cdn.example.com/720.mp4"},
            {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2",
             "tbr":128.0,"language":"fr","url":"https://cdn.example.com/audio.m4a"},
            {"format_id":"hls-720","ext":"mp4","protocol":"m3u8_native",
             "vcodec":"avc1","acodec":"mp4a","height":720,
             "manifest_url":"https://cdn.example.com/vod.m3u8"}
          ],
          "subtitles": {
            "fr": [{"url":"https://cdn.example.com/fr.vtt","name":"Français","ext":"vtt"}],
            "en": [{"url":"https://cdn.example.com/en.vtt","ext":"vtt"}]
          }
        }
    """.trimIndent()

    @Test
    fun `les metadonnees sont reprises`() {
        val media = YtDlpJson.parse(fixture)
        assertEquals("Exemple de vidéo", media.title)
        assertEquals("Une chaîne", media.author)
        assertEquals(754_000L, media.durationMs)
        assertEquals("https://i.example.com/thumb.jpg", media.thumbnailUrl)
        assertEquals("https://exemple.tld/watch?v=abc123", media.canonicalUrl)
        assertEquals("ytdlp-import", media.sourceId)
    }

    @Test
    fun `video et audio sont distingues par le codec`() {
        val media = YtDlpJson.parse(fixture)
        val video = media.variants.filter { it.kind == MediaKind.VIDEO }
        val audio = media.variants.filter { it.kind == MediaKind.AUDIO }

        assertEquals(3, video.size) // 1080, 720, HLS
        assertEquals(1, audio.size)
        assertEquals("fr", audio.single().audioLanguage)
    }

    @Test
    fun `les variantes sont triees par resolution decroissante`() {
        val media = YtDlpJson.parse(fixture)
        val hauteurs = media.variants.filter { it.height != null }.map { it.height }
        assertEquals(listOf(1080, 720, 720), hauteurs)
    }

    @Test
    fun `la taille et le debit viennent de filesize et tbr`() {
        val media = YtDlpJson.parse(fixture)
        val hd = media.variants.first { it.height == 1080 }
        assertEquals(395_000_000L, hd.sizeBytes)
        assertEquals(4200, hd.bitrateKbps)
        assertEquals("1080p", hd.qualityLabel)

        val sd = media.variants.first { it.id.endsWith("22") }
        assertEquals(120_000_000L, sd.sizeBytes) // filesize_approx en secours
    }

    @Test
    fun `les en-tetes http sont conserves avec la variante`() {
        val media = YtDlpJson.parse(fixture)
        val hd = media.variants.first { it.height == 1080 }
        assertEquals("https://exemple.tld/", hd.headers["Referer"])
    }

    @Test
    fun `un manifeste HLS garde son conteneur ts et son URL de manifeste`() {
        val media = YtDlpJson.parse(fixture)
        val hls = media.variants.first { it.id == "ytdlp:hls-720" }
        assertEquals("ts", hls.container)
        assertEquals("https://cdn.example.com/vod.m3u8", hls.url)
    }

    @Test
    fun `les sous-titres sont listes avec leur langue`() {
        val media = YtDlpJson.parse(fixture)
        assertEquals(2, media.subtitles.size)
        assertEquals("fr", media.subtitles.first().language)
        assertEquals("Français", media.subtitles.first().label)
    }

    @Test
    fun `un json sans adresse exploitable est refuse explicitement`() {
        val error = runCatching {
            YtDlpJson.parse("""{"title":"x","formats":[]}""")
        }.exceptionOrNull()
        assertTrue(error is DownloadException)
        assertTrue(error!!.message!!.contains("-J"))
    }

    @Test
    fun `un format sans url ni manifeste est ignore`() {
        val media = YtDlpJson.parse(
            """{"title":"x","formats":[
                 {"format_id":"a","ext":"mp4","vcodec":"avc1","height":480},
                 {"format_id":"b","ext":"mp4","vcodec":"avc1","height":360,
                  "url":"https://cdn.example.com/360.mp4"}]}""",
        )
        assertEquals(1, media.variants.size)
        assertEquals("360p", media.variants.single().qualityLabel)
    }

    @Test
    fun `un json invalide leve une erreur claire`() {
        val error = runCatching { YtDlpJson.parse("pas du json") }.exceptionOrNull()
        assertTrue(error is DownloadException)
    }

    @Test
    fun `sans sous-titre la liste est vide et non nulle`() {
        val media = YtDlpJson.parse(
            """{"title":"x","formats":[{"format_id":"a","ext":"mp4","vcodec":"avc1",
                "url":"https://cdn.example.com/a.mp4"}]}""",
        )
        assertTrue(media.subtitles.isEmpty())
        assertNull(media.variants.single().audioLanguage)
    }
}
