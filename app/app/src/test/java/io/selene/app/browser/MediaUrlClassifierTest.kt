package io.selene.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Le navigateur ne doit retenir que ce qui est réellement téléchargeable, et
 * surtout ne pas confondre un segment isolé avec un média complet : c'est
 * l'erreur qui donne un fichier de trois secondes à l'utilisateur.
 */
class MediaUrlClassifierTest {

    @Test
    fun `reconnait les manifestes`() {
        assertThat(MediaUrlClassifier.classify("https://a.tld/live/index.m3u8"))
            .isEqualTo(DetectedKind.MANIFEST)
        assertThat(MediaUrlClassifier.classify("https://a.tld/dash/manifest.mpd?token=42"))
            .isEqualTo(DetectedKind.MANIFEST)
        assertThat(MediaUrlClassifier.classify("https://a.tld/x", "application/vnd.apple.mpegurl"))
            .isEqualTo(DetectedKind.MANIFEST)
        assertThat(MediaUrlClassifier.classify("https://a.tld/x", "application/dash+xml"))
            .isEqualTo(DetectedKind.MANIFEST)
    }

    @Test
    fun `distingue video et audio par extension et par type MIME`() {
        assertThat(MediaUrlClassifier.classify("https://a.tld/clip.mp4")).isEqualTo(DetectedKind.VIDEO)
        assertThat(MediaUrlClassifier.classify("https://a.tld/clip.MKV")).isEqualTo(DetectedKind.VIDEO)
        assertThat(MediaUrlClassifier.classify("https://a.tld/song.m4a")).isEqualTo(DetectedKind.AUDIO)
        assertThat(MediaUrlClassifier.classify("https://a.tld/x", "audio/mpeg")).isEqualTo(DetectedKind.AUDIO)
        assertThat(MediaUrlClassifier.classify("https://a.tld/x", "video/webm")).isEqualTo(DetectedKind.VIDEO)
    }

    @Test
    fun `un segment isole est signale comme tel`() {
        assertThat(MediaUrlClassifier.classify("https://a.tld/hls/seg-00042.ts"))
            .isEqualTo(DetectedKind.SEGMENT)
        assertThat(MediaUrlClassifier.classify("https://a.tld/dash/chunk-9.m4s"))
            .isEqualTo(DetectedKind.SEGMENT)
    }

    @Test
    fun `les ressources non medias sont ignorees`() {
        assertThat(MediaUrlClassifier.classify("https://a.tld/style.css")).isNull()
        assertThat(MediaUrlClassifier.classify("https://a.tld/logo.png")).isNull()
        assertThat(MediaUrlClassifier.classify("https://a.tld/api/items.json")).isNull()
        assertThat(MediaUrlClassifier.classify("https://a.tld/image.jpeg?w=200")).isNull()
        assertThat(MediaUrlClassifier.detect("https://a.tld/app.js")).isNull()
    }

    @Test
    fun `les adresses blob sont retenues mais marquees opaques`() {
        val detected = MediaUrlClassifier.detect("blob:https://a.tld/9f2b-11ee")
        assertThat(detected).isNotNull()
        assertThat(detected!!.kind).isEqualTo(DetectedKind.OPAQUE)
    }

    @Test
    fun `une adresse relative ou sans schema n est pas retenue`() {
        assertThat(MediaUrlClassifier.detect("/media/clip.mp4")).isNull()
        assertThat(MediaUrlClassifier.detect("javascript:void(0)")).isNull()
        assertThat(MediaUrlClassifier.detect("   ")).isNull()
    }

    @Test
    fun `le fragment ne cree pas de doublon`() {
        val a = MediaUrlClassifier.detect("https://a.tld/clip.mp4")
        val b = MediaUrlClassifier.detect("https://a.tld/clip.mp4#t=42")
        assertThat(a).isNotNull()
        assertThat(b).isNotNull()
        assertThat(a!!.key).isEqualTo(b!!.key)
    }

    @Test
    fun `un point dans un dossier ne fait pas une extension`() {
        // « a.tld » contient un point sans être un fichier.
        assertThat(MediaUrlClassifier.classify("https://cdn.a.tld/")).isNull()
        assertThat(MediaUrlClassifier.classify("https://cdn.a.tld/video/")).isNull()
    }
}
