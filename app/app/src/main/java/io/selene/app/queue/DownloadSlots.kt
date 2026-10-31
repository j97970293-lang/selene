package io.selene.app.queue

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * Limite le nombre de téléchargements simultanés, réglable de 1 à 4.
 *
 * Pourquoi une porte plutôt qu'un sémaphore figé : le réglage peut changer pendant que des
 * téléchargements tournent. Une porte qui relit le maximum courant à chaque acquisition suit le
 * changement sans recréer de sémaphore, et sans interrompre ce qui est en cours.
 *
 * Le minimum est 1 : autoriser 0 bloquerait la file sans que rien ne le dise.
 */
class DownloadSlots(
    initialMax: Int = 1,
    private val pollIntervalMs: Long = 50,
) {

    private val active = AtomicInteger(0)
    private val max = AtomicInteger(initialMax.coerceIn(MIN, MAX))

    fun setMax(value: Int) {
        max.set(value.coerceIn(MIN, MAX))
    }

    fun currentMax(): Int = max.get()

    fun activeCount(): Int = active.get()

    suspend fun <T> withSlot(block: suspend () -> T): T {
        while (true) {
            val current = active.get()
            if (current < max.get() && active.compareAndSet(current, current + 1)) break
            delay(pollIntervalMs)
        }
        try {
            return block()
        } finally {
            active.decrementAndGet()
        }
    }

    companion object {
        const val MIN = 1
        const val MAX = 4
    }
}
