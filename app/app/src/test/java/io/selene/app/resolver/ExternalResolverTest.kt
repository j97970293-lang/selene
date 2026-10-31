package io.selene.app.resolver

import io.selene.app.data.UserSettings
import io.selene.app.model.DownloadException
import io.selene.app.model.MediaKind
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Tests JVM du client de pont externe : aucun appareil, aucun réseau réel (MockWebServer).
 * On vérifie surtout les garde-fous, qui comptent plus que le cas heureux : désactivé par
 * défaut, jamais sollicité pour un fichier direct, jeton obligatoire, schéma vérifié.
 */
class ExternalResolverTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun resolver(enabled: Boolean = true, token: String = "jeton-test"): ExternalResolver =
        ExternalResolver(OkHttpClient()).apply {
            applySettings(
                UserSettings(
                    externalResolverEnabled = enabled,
                    externalResolverEndpoint = server.url("/").toString(),
                    externalResolverToken = token,
                ),
            )
        }

    @Test
    fun `desactive par defaut, aucune adresse ne correspond`() {
        val resolver = ExternalResolver(OkHttpClient())
        assertFalse(resolver.matches("https://exemple.tld/page/123"))
    }

    @Test
    fun `active sans jeton, le pont reste inutilisable`() {
        val resolver = ExternalResolver(OkHttpClient()).apply {
            applySettings(
                UserSettings(
                    externalResolverEnabled = true,
                    externalResolverEndpoint = "https://pont.tld",
                    externalResolverToken = "",
                ),
            )
        }
        assertFalse(resolver.matches("https://exemple.tld/page/123"))
    }

    @Test
    fun `un pont en clair sur internet est refuse`() {
        val resolver = ExternalResolver(OkHttpClient()).apply {
            applySettings(
                UserSettings(
                    externalResolverEnabled = true,
                    externalResolverEndpoint = "http://pont.example.com",
                    externalResolverToken = "jeton",
                ),
            )
        }
        // Le jeton ne doit pas partir en clair sur le réseau.
        assertFalse(resolver.matches("https://exemple.tld/page"))
    }

    @Test
    fun `un pont en clair sur la boucle locale reste accepte`() {
        val resolver = ExternalResolver(OkHttpClient()).apply {
            applySettings(
                UserSettings(
                    externalResolverEnabled = true,
                    externalResolverEndpoint = "http://127.0.0.1:8080",
                    externalResolverToken = "jeton",
                ),
            )
        }
        assertTrue(resolver.matches("https://exemple.tld/page"))
    }

    @Test
    fun `un fichier media direct ne passe jamais par le pont`() {
        val resolver = resolver()
        assertFalse(resolver.matches("https://cdn.tld/video.mp4"))
        assertFalse(resolver.matches("https://cdn.tld/flux.m3u8"))
        assertFalse(resolver.matches("https://cdn.tld/son.mp3?token=abc"))
        assertTrue(resolver.matches("https://exemple.tld/page/123"))
    }

    @Test
    fun `une reponse conforme produit des variantes exploitables`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "schema": "selene.external/1",
                  "title": "Titre",
                  "duration_ms": 754000,
                  "variants": [
                    {"id":"1080p","label":"1080p","container":"mp4","mime":"video/mp4",
                     "width":1920,"height":1080,"bitrate":4200000,"size_bytes":395000000,
                     "url":"https://cdn.tld/1080.mp4"},
                    {"id":"audio","container":"m4a","mime":"audio/mp4","bitrate":128000,
                     "url":"https://cdn.tld/audio.m4a","language":"fr"}
                  ],
                  "subtitles": [
                    {"lang":"fr","label":"Français","format":"vtt","url":"https://cdn.tld/fr.vtt"}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val media = resolver().resolve("https://exemple.tld/page/123")

        assertEquals("Titre", media.title)
        assertEquals(2, media.variants.size)
        assertEquals(MediaKind.VIDEO, media.variants[0].kind)
        assertEquals("1080p", media.variants[0].qualityLabel)
        assertEquals(4200, media.variants[0].bitrateKbps)
        assertEquals(MediaKind.AUDIO, media.variants[1].kind)
        assertEquals("fr", media.variants[1].audioLanguage)
        assertEquals(1, media.subtitles.size)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/resolve", request.path)
        assertEquals("Bearer jeton-test", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("https://exemple.tld/page/123"))
    }

    @Test
    fun `un jeton refuse donne un message clair, sans reessai`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val error = runCatching { resolver().resolve("https://exemple.tld/p") }.exceptionOrNull()
        assertTrue(error is DownloadException)
        assertFalse((error as DownloadException).retryable)
        assertTrue(error.message!!.contains("jeton"))
    }

    @Test
    fun `un pont indisponible est reessayable`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val error = runCatching { resolver().resolve("https://exemple.tld/p") }.exceptionOrNull()
        assertTrue((error as DownloadException).retryable)
    }

    @Test
    fun `un schema inattendu est refuse`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"schema":"autre.produit/9","variants":[]}""",
            ),
        )
        val error = runCatching { resolver().resolve("https://exemple.tld/p") }.exceptionOrNull()
        assertTrue(error is DownloadException)
        assertTrue(error!!.message!!.contains("schéma"))
    }

    @Test
    fun `un json illisible ne fait pas planter le resolveur`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("pas du json"))
        val error = runCatching { resolver().resolve("https://exemple.tld/p") }.exceptionOrNull()
        assertTrue(error is DownloadException)
    }

    @Test
    fun `une reponse sans variante est une erreur explicite`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"schema":"selene.external/1","variants":[]}"""),
        )
        val error = runCatching { resolver().resolve("https://exemple.tld/p") }.exceptionOrNull()
        assertTrue(error is DownloadException)
    }

    @Test
    fun `le corps de requete echappe les guillemets et les sauts de ligne`() {
        assertEquals("\"a\\\"b\"", "a\"b".jsonQuoted())
        assertEquals("\"a\\nb\"", "a\nb".jsonQuoted())
        assertEquals("\"c:\\\\x\"", "c:\\x".jsonQuoted())
    }

    @Test
    fun `le registre bascule sur le pont quand le resolveur direct echoue`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"schema":"selene.external/1","title":"Pont","variants":[
                     {"id":"v","container":"mp4","url":"https://cdn.tld/v.mp4"}]}""",
            ),
        )
        // Le résolveur direct est prioritaire et réussit sur cette adresse : le pont n'est pas
        // sollicité. Ce test verrouille l'ordre de priorité.
        val failing = object : MediaResolver {
            override val id = "always-fail"
            override val priority = 50
            override fun matches(url: String) = true
            override suspend fun resolve(url: String) = throw DownloadException("refus", false)
        }
        val direct = object : MediaResolver {
            override val id = "direct-like"
            override val priority = 0
            override fun matches(url: String) = true
            override suspend fun resolve(url: String) =
                io.selene.app.model.ResolvedMedia(
                    sourceId = "direct-like",
                    canonicalUrl = url,
                    title = "direct",
                    variants = emptyList(),
                )
        }

        val registry = ResolverRegistry(setOf(failing, direct, resolver()))
        val media = registry.resolve("https://exemple.tld/p")
        assertEquals("direct", media.title)
    }
}
