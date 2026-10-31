package io.selene.app.downloading

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Un mauvais classement range un MP3 dans « Films » : invisible dans le lecteur. */
class MediaMimeTest {

    @Test
    fun `famille par extension`() {
        assertThat(MediaMime.familyFor("mp4")).isEqualTo(MediaFamily.VIDEO)
        assertThat(MediaMime.familyFor("MKV")).isEqualTo(MediaFamily.VIDEO)
        assertThat(MediaMime.familyFor("mp3")).isEqualTo(MediaFamily.AUDIO)
        assertThat(MediaMime.familyFor(".m4a")).isEqualTo(MediaFamily.AUDIO)
        assertThat(MediaMime.familyFor("srt")).isEqualTo(MediaFamily.DOCUMENT)
        assertThat(MediaMime.familyFor("")).isEqualTo(MediaFamily.DOCUMENT)
    }

    @Test
    fun `chemin MediaStore par famille`() {
        assertThat(MediaMime.relativePath(MediaFamily.VIDEO)).isEqualTo("Movies/Selene")
        assertThat(MediaMime.relativePath(MediaFamily.AUDIO)).isEqualTo("Music/Selene")
        assertThat(MediaMime.relativePath(MediaFamily.DOCUMENT)).isEqualTo("Download/Selene")
    }

    @Test
    fun `type MIME connu et repli pour l inconnu`() {
        assertThat(MediaMime.mimeFor("mp4")).isEqualTo("video/mp4")
        assertThat(MediaMime.mimeFor("m4a")).isEqualTo("audio/mp4")
        assertThat(MediaMime.mimeFor("vtt")).isEqualTo("text/vtt")
        assertThat(MediaMime.mimeFor("xyz")).isEqualTo("application/octet-stream")
    }

    @Test
    fun `extension depuis un nom de fichier ou une adresse`() {
        assertThat(MediaMime.extensionOf("Séléné - épisode 1.mp4")).isEqualTo("mp4")
        assertThat(MediaMime.extensionOf("https://a.tld/v/clip.MP4?token=abc")).isEqualTo("mp4")
        assertThat(MediaMime.extensionOf("https://a.tld/sans-extension")).isEqualTo("")
        assertThat(MediaMime.extensionOf("https://a.tld/")).isEqualTo("")
    }

    @Test
    fun `un dossier nomme avec un point ne produit pas d extension`() {
        assertThat(MediaMime.extensionOf("https://cdn.a.tld/video/file")).isEqualTo("")
        // Un point dans le dernier segment mais pas d'extension plausible.
        assertThat(MediaMime.extensionOf("https://a.tld/1.2.3")).isEqualTo("")
    }
}
