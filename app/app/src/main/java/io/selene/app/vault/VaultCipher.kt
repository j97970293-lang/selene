package io.selene.app.vault

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement de fichiers par blocs, en flux — sans dépendance Android, donc testable sur JVM.
 *
 * Format du conteneur :
 *
 * ```
 * "SLNV" | version(1) | taille_de_bloc(4) | bloc* n
 * ```
 *
 * avec, pour chaque bloc : `nonce(12) | longueur(4) | texte chiffré + tag GCM(16)`.
 *
 * Pourquoi par blocs : un fichier de 2 Go ne doit pas être chargé en mémoire pour être chiffré.
 * Chaque bloc porte son propre nonce aléatoire, jamais réutilisé — c'est la condition de sûreté
 * de GCM : un nonce répété avec la même clé casse tout le chiffrement.
 */
object VaultCipher {

    private const val MAGIC = "SLNV"
    private const val VERSION: Byte = 1
    private const val DEFAULT_CHUNK = 64 * 1024
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    fun encrypt(input: InputStream, output: OutputStream, key: SecretKey, chunkSize: Int = DEFAULT_CHUNK) {
        require(key.encoded != null) { "clé sans encodage : fournir un SecretKeySpec" }
        val header = MAGIC.toByteArray(Charsets.US_ASCII)
        output.write(header)
        output.write(byteArrayOf(VERSION))
        output.write(intToBytes(chunkSize))

        val buffer = ByteArray(chunkSize)
        while (true) {
            val read = input.readFully(buffer)
            if (read < 0) break
            val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            }
            val sealed = cipher.doFinal(buffer, 0, read)
            output.write(nonce)
            output.write(intToBytes(sealed.size))
            output.write(sealed)
        }
        output.flush()
    }

    fun decrypt(input: InputStream, output: OutputStream, key: SecretKey) {
        val magic = ByteArray(4)
        if (input.readFully(magic) != 4 || String(magic, Charsets.US_ASCII) != MAGIC) {
            throw VaultFormatException("Ce fichier n'est pas un conteneur Selene")
        }
        val version = input.read()
        if (version != VERSION.toInt()) {
            throw VaultFormatException("Version de conteneur inconnue : $version")
        }
        val chunkSize = bytesToInt(input.readExact(4))
        if (chunkSize <= 0 || chunkSize > 8 * 1024 * 1024) {
            throw VaultFormatException("Taille de bloc invalide : $chunkSize")
        }

        while (true) {
            val nonce = ByteArray(NONCE_SIZE)
            val nonceRead = input.readFully(nonce)
            // -1 = fin de flux atteinte proprement (aucun octet de nonce disponible).
            if (nonceRead < 0) break
            if (nonceRead != NONCE_SIZE) {
                throw VaultFormatException("Flux tronqué : nonce incomplet")
            }
            val length = bytesToInt(input.readExact(4))
            if (length <= 0 || length > chunkSize + TAG_BITS / 8) {
                throw VaultFormatException("Longueur de bloc invalide : $length")
            }
            val sealed = input.readExact(length)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            }
            val clear = try {
                cipher.doFinal(sealed)
            } catch (error: javax.crypto.AEADBadTagException) {
                throw VaultFormatException("Clé incorrecte ou fichier modifié", error)
            }
            output.write(clear)
        }
        output.flush()
    }

    /** Clé AES-256 dérivée d'un secret — utilisée pour les tests et pour un export portable. */
    fun keyFrom(bytes: ByteArray): SecretKey = SecretKeySpec(bytes.copyOf(32), "AES")

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) return if (total == 0) -1 else total
            total += read
        }
        return total
    }

    private fun InputStream.readExact(size: Int): ByteArray {
        val data = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(data, offset, size - offset)
            if (read < 0) throw VaultFormatException("Flux tronqué : $offset/$size octets lus")
            offset += read
        }
        return data
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    // Parenthèses explicites : la priorité des fonctions infixes (`and`, `shl`) se lit mal.
    private fun bytesToInt(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
}

class VaultFormatException(message: String, cause: Throwable? = null) : IOException(message, cause)
