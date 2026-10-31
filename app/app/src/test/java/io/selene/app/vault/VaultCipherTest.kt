package io.selene.app.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class VaultCipherTest {

    private val key = VaultCipher.keyFrom(Random(42).nextBytes(32))
    private val otherKey = VaultCipher.keyFrom(Random(7).nextBytes(32))

    private fun encrypt(plain: ByteArray, chunk: Int = 4096, k: javax.crypto.SecretKey = key): ByteArray {
        val out = ByteArrayOutputStream()
        VaultCipher.encrypt(ByteArrayInputStream(plain), out, k, chunk)
        return out.toByteArray()
    }

    private fun decrypt(sealed: ByteArray, k: javax.crypto.SecretKey = key): ByteArray {
        val out = ByteArrayOutputStream()
        VaultCipher.decrypt(ByteArrayInputStream(sealed), out, k)
        return out.toByteArray()
    }

    @Test
    fun `un aller-retour rend le fichier identique`() {
        val plain = Random(1).nextBytes(200_000)
        assertTrue(decrypt(encrypt(plain)).contentEquals(plain))
    }

    @Test
    fun `un fichier vide survit a l aller-retour`() {
        assertEquals(0, decrypt(encrypt(ByteArray(0))).size)
    }

    @Test
    fun `un fichier plus petit qu un bloc est gere`() {
        val plain = "bonjour".toByteArray()
        assertTrue(decrypt(encrypt(plain, chunk = 64 * 1024)).contentEquals(plain))
    }

    @Test
    fun `le texte chiffre ne contient pas le clair`() {
        val plain = "SECRET-EN-CLAIR-1234567890".repeat(100).toByteArray()
        val sealed = encrypt(plain)
        val haystack = String(sealed, Charsets.ISO_8859_1)
        assertTrue(!haystack.contains("SECRET-EN-CLAIR"))
    }

    @Test
    fun `deux chiffrements du meme contenu different`() {
        val plain = "identique".toByteArray()
        val a = encrypt(plain)
        val b = encrypt(plain)
        // Nonces aléatoires par bloc : deux sorties ne doivent jamais coïncider.
        assertTrue(!a.contentEquals(b))
        assertEquals(plain.size, decrypt(a).size)
        assertEquals(plain.size, decrypt(b).size)
    }

    @Test(expected = VaultFormatException::class)
    fun `une mauvaise cle est refusee`() {
        val sealed = encrypt("données".toByteArray(), k = key)
        decrypt(sealed, k = otherKey)
    }

    @Test(expected = VaultFormatException::class)
    fun `un octet modifie invalide le bloc`() {
        val sealed = encrypt(Random(3).nextBytes(10_000))
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        decrypt(sealed)
    }

    @Test(expected = VaultFormatException::class)
    fun `un flux tronque est signale`() {
        val sealed = encrypt(Random(4).nextBytes(10_000))
        decrypt(sealed.copyOf(sealed.size - 100))
    }

    @Test(expected = VaultFormatException::class)
    fun `un fichier etranger est refuse`() {
        decrypt("ceci n'est pas un conteneur".toByteArray())
    }
}
