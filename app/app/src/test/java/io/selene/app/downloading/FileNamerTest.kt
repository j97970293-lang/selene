package io.selene.app.downloading

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileNamerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `strips accents and forbidden characters`() {
        val name = FileNamer.sanitize("Démo: l'été / été?", "mp4")

        assertThat(name).isEqualTo("Demo l'ete ete.mp4")
    }

    @Test
    fun `falls back to a neutral name when nothing is left`() {
        assertThat(FileNamer.sanitize("///", "mp4")).isEqualTo("media.mp4")
    }

    @Test
    fun `truncates long titles but keeps the extension`() {
        val name = FileNamer.sanitize("a".repeat(400), "webm")

        assertThat(name.length).isAtMost(124)
        assertThat(name.endsWith(".webm")).isTrue()
    }

    @Test
    fun `appends a counter instead of overwriting`() {
        val directory = temporaryFolder.newFolder()
        File(directory, "clip.mp4").writeText("first")

        val second = FileNamer.uniqueFile(directory, "clip.mp4")
        assertThat(second.name).isEqualTo("clip (2).mp4")

        second.writeText("second")
        val third = FileNamer.uniqueFile(directory, "clip.mp4")
        assertThat(third.name).isEqualTo("clip (3).mp4")
    }

    @Test
    fun `handles a name without extension`() {
        val directory = temporaryFolder.newFolder()
        File(directory, "README").writeText("x")

        assertThat(FileNamer.uniqueFile(directory, "README").name).isEqualTo("README (2)")
    }
}
