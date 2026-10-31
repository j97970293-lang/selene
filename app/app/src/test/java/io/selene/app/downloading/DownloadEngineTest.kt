package io.selene.app.downloading

import com.google.common.truth.Truth.assertThat
import io.selene.app.model.DownloadException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests JVM : aucun appareil, aucun accès réseau externe.
 * C'est ce qui permet de valider le moteur sans consommer de données mobiles.
 */
class DownloadEngineTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var engine: DownloadEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = DownloadEngine(
            client = OkHttpClient(),
            maxAttempts = 3,
            initialBackoffMs = 1,
            sleeper = { },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun target(): File = File(temporaryFolder.newFolder(), "media.mp4")

    @Test
    fun `downloads a complete body`() = runBlocking {
        val payload = "abcdefghij".toByteArray()
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))

        val progress = mutableListOf<Pair<Long, Long?>>()
        val file = engine.download(server.url("/v.mp4").toString(), target()) { done, total ->
            progress += done to total
        }

        assertThat(file.readBytes()).isEqualTo(payload)
        assertThat(File("${file.path}.part").exists()).isFalse()
        assertThat(progress.last().first).isEqualTo(payload.size.toLong())
    }

    @Test
    fun `resumes from a partial file with a Range request`() = runBlocking {
        val full = "0123456789".toByteArray()
        val already = 4

        val destination = target()
        File("${destination.path}.part").writeBytes(full.copyOfRange(0, already))

        var requestedRange: String? = null
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestedRange = request.getHeader("Range")
                val body = full.copyOfRange(already, full.size)
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $already-${full.size - 1}/${full.size}")
                    .setBody(Buffer().write(body))
            }
        }

        val file = engine.download(server.url("/v.mp4").toString(), destination)

        assertThat(requestedRange).isEqualTo("bytes=$already-")
        assertThat(file.readBytes()).isEqualTo(full)
    }

    @Test
    fun `restarts from zero when the server ignores Range`() = runBlocking {
        val full = "0123456789".toByteArray()
        val destination = target()
        File("${destination.path}.part").writeBytes(full.copyOfRange(0, 4))

        // 200 malgré l'en-tête Range : le serveur ne sait pas reprendre.
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(full)))

        val file = engine.download(server.url("/v.mp4").toString(), destination)

        // Le fichier doit être complet et non dupliqué (pas de 4 + 10 octets).
        assertThat(file.readBytes()).isEqualTo(full)
    }

    @Test
    fun `treats 416 as already complete`() = runBlocking {
        val full = "complete".toByteArray()
        val destination = target()
        File("${destination.path}.part").writeBytes(full)

        server.enqueue(MockResponse().setResponseCode(416))

        val file = engine.download(server.url("/v.mp4").toString(), destination)

        assertThat(file.readBytes()).isEqualTo(full)
    }

    @Test
    fun `retries transient failures then succeeds`() = runBlocking {
        val payload = "retry-ok".toByteArray()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))

        val file = engine.download(server.url("/v.mp4").toString(), target())

        assertThat(file.readBytes()).isEqualTo(payload)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `does not retry a definitive client error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(Buffer().write("never".toByteArray())))

        val failure = runCatching {
            engine.download(server.url("/v.mp4").toString(), target())
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(DownloadException::class.java)
        assertThat((failure as DownloadException).retryable).isFalse()
        assertThat(server.requestCount).isEqualTo(1)
    }
}
