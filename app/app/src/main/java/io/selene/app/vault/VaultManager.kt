package io.selene.app.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Coffre local : les fichiers placés ici sont chiffrés (AES-256-GCM par blocs) et sortent du
 * dossier média. La clé ne quitte jamais l'Android Keystore — elle n'est ni écrite sur le disque
 * ni exportable, et elle est liée au matériel quand l'appareil en dispose.
 *
 * Contrat assumé, à écrire dans les réglages plutôt que dans un commentaire : **perdre la clé
 * (réinstallation, effacement du Keystore) rend les fichiers du coffre irrécupérables.** C'est le
 * prix d'un stockage réellement chiffré ; un coffre qu'on peut rouvrir sans la clé n'en est pas un.
 *
 * Ce que ce coffre n'est pas : une protection contre quelqu'un qui a ton téléphone déverrouillé et
 * l'application ouverte. Pour cela il faut un code d'accès à l'application, qui est une étape
 * supplémentaire (verrou biométrique avant l'ouverture de l'écran).
 */
class VaultManager(private val context: Context) {

    private val vaultDir: File
        get() = File(context.filesDir, "vault").apply { mkdirs() }

    /**
     * Déplace un fichier téléchargé dans le coffre.
     * @return le fichier chiffré, l'original ayant été supprimé.
     */
    suspend fun moveIntoVault(source: File): File = withContext(Dispatchers.IO) {
        if (!source.exists()) throw VaultFormatException("Fichier introuvable : ${source.name}")
        val target = File(vaultDir, source.name + ENCRYPTED_SUFFIX)

        source.inputStream().use { input ->
            target.outputStream().use { output ->
                VaultCipher.encrypt(input, output, masterKey())
            }
        }

        // On ne supprime l'original qu'une fois le fichier chiffré écrit et fermé.
        source.delete()
        target
    }

    /** Ressort un fichier du coffre en clair, dans [destination]. */
    suspend fun restore(encrypted: File, destination: File): File = withContext(Dispatchers.IO) {
        encrypted.inputStream().use { input ->
            destination.outputStream().use { output ->
                VaultCipher.decrypt(input, output, masterKey())
            }
        }
        destination
    }

    fun list(): List<File> = vaultDir.listFiles()
        ?.filter { it.name.endsWith(ENCRYPTED_SUFFIX) }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()

    fun sizeBytes(): Long = list().sumOf { it.length() }

    /** Suppression définitive, sans passer par la corbeille. */
    fun delete(encrypted: File): Boolean = encrypted.delete()

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Pas d'authentification utilisateur : le coffre doit pouvoir se remplir en
                // tâche de fond. Le verrou d'écran, lui, protège déjà le Keystore au repos.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "selene.vault.master"
        const val ENCRYPTED_SUFFIX = ".selene"
    }
}
