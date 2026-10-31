package io.selene.app.downloading

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Les messages de yt-dlp sortent en anglais technique. Ce qui compte pour
 * l'utilisateur, c'est de savoir si réessayer a un sens - une limite de débit
 * se réessaie, un contenu privé non.
 */
class YtDlpErrorsTest {

    @Test
    fun `adresse non prise en charge - definitif`() {
        val failure = YtDlpErrors.describe("ERROR: Unsupported URL: https://exemple.tld/x")
        assertThat(failure.retryable).isFalse()
        assertThat(failure.message).contains("pas pris en charge")
    }

    @Test
    fun `connexion coupee - reessayable`() {
        val failure = YtDlpErrors.describe("ERROR: Unable to download webpage: <urlopen error timed out>")
        assertThat(failure.retryable).isTrue()
        assertThat(failure.message).contains("Connexion")
    }

    @Test
    fun `limite de debit - reessayable avec delai`() {
        val failure = YtDlpErrors.describe("ERROR: HTTP Error 429: Too Many Requests")
        assertThat(failure.retryable).isTrue()
        assertThat(failure.message).contains("429")
    }

    @Test
    fun `contenu prive - definitif et explicite sur la limite`() {
        val failure = YtDlpErrors.describe("ERROR: Sign in to confirm you're not a bot")
        assertThat(failure.retryable).isFalse()
        assertThat(failure.message).contains("compte")
    }

    @Test
    fun `erreur serveur du site - reessayable`() {
        assertThat(YtDlpErrors.describe("ERROR: HTTP Error 503: Service Unavailable").retryable).isTrue()
    }

    @Test
    fun `fusion ffmpeg - reessayable avec avertissement de fichier incomplet`() {
        val failure = YtDlpErrors.describe("[ffmpeg] Merging formats failed")
        assertThat(failure.retryable).isTrue()
        assertThat(failure.message).contains("fusion")
    }

    @Test
    fun `moteur absent - definitif`() {
        val failure = YtDlpErrors.describe("YoutubeDLException: python not found")
        assertThat(failure.retryable).isFalse()
        assertThat(failure.message).contains("moteur yt-dlp")
    }

    @Test
    fun `disque plein - definitif`() {
        assertThat(YtDlpErrors.describe("OSError: No space left on device").retryable).isFalse()
    }

    @Test
    fun `message inconnu conserve mais tronque`() {
        val failure = YtDlpErrors.describe("W".repeat(400))
        assertThat(failure.retryable).isTrue()
        assertThat(failure.message.length).isAtMost(240)
    }

    @Test
    fun `message vide`() {
        val failure = YtDlpErrors.describe(null)
        assertThat(failure.retryable).isTrue()
        assertThat(failure.message).isNotEmpty()
    }
}
