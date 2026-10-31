package io.selene.app.queue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DownloadSlotsTest {

    @Test
    fun `le nombre de telechargements simultanes ne depasse pas le maximum`() = runBlocking {
        val slots = DownloadSlots(initialMax = 2, pollIntervalMs = 5)
        val running = AtomicInteger(0)
        val peak = AtomicInteger(0)

        (1..8).map {
            async(Dispatchers.Default) {
                slots.withSlot {
                    val now = running.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, now) }
                    delay(20)
                    running.decrementAndGet()
                }
            }
        }.awaitAll()

        assertTrue("pic observé : ${peak.get()}", peak.get() <= 2)
        assertEquals(0, slots.activeCount())
    }

    @Test
    fun `un maximum de 4 est respecte`() = runBlocking {
        val slots = DownloadSlots(initialMax = 4, pollIntervalMs = 5)
        val running = AtomicInteger(0)
        val peak = AtomicInteger(0)

        (1..10).map {
            async(Dispatchers.Default) {
                slots.withSlot {
                    val now = running.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, now) }
                    delay(15)
                    running.decrementAndGet()
                }
            }
        }.awaitAll()

        assertTrue("pic observé : ${peak.get()}", peak.get() <= 4)
    }

    @Test
    fun `un maximum hors bornes est ramene dans 1 a 4`() {
        val slots = DownloadSlots(initialMax = 99)
        assertEquals(4, slots.currentMax())

        slots.setMax(0)
        assertEquals(1, slots.currentMax())

        slots.setMax(-3)
        assertEquals(1, slots.currentMax())

        slots.setMax(3)
        assertEquals(3, slots.currentMax())
    }

    @Test
    fun `le slot est rendu meme si le bloc echoue`() = runBlocking {
        val slots = DownloadSlots(initialMax = 1, pollIntervalMs = 5)
        runCatching { slots.withSlot { throw IllegalStateException("échec simulé") } }
        assertEquals(0, slots.activeCount())

        // La porte doit rester utilisable : sans remise du slot, ce second appel bloquerait.
        slots.withSlot { }
        assertEquals(0, slots.activeCount())
    }

    @Test
    fun `abaisser le maximum en cours de route ne bloque pas la file`() = runBlocking {
        val slots = DownloadSlots(initialMax = 4, pollIntervalMs = 5)
        val done = AtomicInteger(0)

        val first = (1..4).map {
            async(Dispatchers.Default) {
                slots.withSlot {
                    delay(10)
                    done.incrementAndGet()
                }
            }
        }

        slots.setMax(1)
        delay(5)
        slots.setMax(2)

        val second = (1..4).map {
            async(Dispatchers.Default) { slots.withSlot { done.incrementAndGet() } }
        }

        // Les deux vagues doivent être attendues : sinon on mesure avant la fin de la première.
        (first + second).awaitAll()
        assertEquals(8, done.get())
    }
}
