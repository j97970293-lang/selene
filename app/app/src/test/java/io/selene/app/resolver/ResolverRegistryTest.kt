package io.selene.app.resolver

import io.selene.app.model.DownloadException
import io.selene.app.model.ResolvedMedia
import io.selene.app.model.UnsupportedContentException
import io.selene.app.model.Variant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le registre doit choisir *quoi dire* quand tous les résolveurs échouent.
 * Un « type non pris en charge » générique est moins utile qu'un vrai 404.
 */
class ResolverRegistryTest {

    private fun resolver(
        id: String,
        priority: Int,
        onResolve: suspend (String) -> ResolvedMedia,
    ): MediaResolver = object : MediaResolver {
        override val id = id
        override val priority = priority
        override fun matches(url: String) = true
        override suspend fun resolve(url: String) = onResolve(url)
    }

    private fun ok(title: String) = ResolvedMedia(
        sourceId = "test",
        canonicalUrl = "https://exemple.tld/x",
        title = title,
        variants = listOf(
            Variant(id = "v", kind = io.selene.app.model.MediaKind.VIDEO, container = "mp4", url = "https://cdn.tld/v.mp4"),
        ),
    )

    @Test
    fun `le premier resolveur qui repond gagne`() = runBlocking {
        val registry = ResolverRegistry(
            setOf(
                resolver("a", 100) { ok("a") },
                resolver("b", 50) { ok("b") },
            ),
        )
        assertEquals("a", registry.resolve("https://exemple.tld/x").title)
    }

    @Test
    fun `un echec ne condamne pas l adresse`() = runBlocking {
        val registry = ResolverRegistry(
            setOf(
                resolver("a", 100) { throw DownloadException("HTTP 404", retryable = false) },
                resolver("b", 50) { ok("b") },
            ),
        )
        assertEquals("b", registry.resolve("https://exemple.tld/x").title)
    }

    @Test
    fun `tous les resolveurs disent non-media, le message reste actionnable`() = runBlocking {
        val registry = ResolverRegistry(
            setOf(
                resolver("a", 100) { throw UnsupportedContentException("Type de contenu non pris en charge : text/html") },
                resolver("b", 50) { throw UnsupportedContentException("pas un média") },
            ),
        )
        val error = runCatching { registry.resolve("https://exemple.tld/page") }.exceptionOrNull()
        assertTrue(error is UnsupportedContentException)
        assertTrue(error!!.message!!.contains("yt-dlp"))
    }

    @Test
    fun `une erreur precise est preferee a un type non pris en charge`() = runBlocking {
        val registry = ResolverRegistry(
            setOf(
                resolver("a", 100) { throw UnsupportedContentException("text/html") },
                resolver("b", 50) { throw DownloadException("HTTP 403 : accès refusé", retryable = false) },
            ),
        )
        val error = runCatching { registry.resolve("https://exemple.tld/page") }.exceptionOrNull()
        assertTrue(error is DownloadException)
        assertTrue(error!!.message!!.contains("403"))
    }

    @Test
    fun `aucun resolveur compatible donne NoResolver`() = runBlocking {
        val registry = ResolverRegistry(
            setOf(
                object : MediaResolver {
                    override val id = "strict"
                    override fun matches(url: String) = url.contains("special")
                    override suspend fun resolve(url: String) = ok("x")
                },
            ),
        )
        val error = runCatching { registry.resolve("https://exemple.tld/autre") }.exceptionOrNull()
        assertTrue(error is NoResolverException)
    }
}
