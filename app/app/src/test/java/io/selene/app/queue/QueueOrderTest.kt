package io.selene.app.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOrderTest {

    private fun entry(id: String, priority: Int = 0, position: Long) = QueueEntry(id, priority, position)

    @Test
    fun `la priorite passe avant la position`() {
        val entries = listOf(
            entry("a", position = 0),
            entry("urgent", priority = 10, position = 99),
            entry("c", position = 1),
        )
        assertEquals(listOf("urgent", "a", "c"), QueueOrder.sorted(entries).map { it.id })
    }

    @Test
    fun `a priorite egale l ordre choisi est respecte`() {
        val entries = listOf(entry("b", position = 1), entry("a", position = 0))
        assertEquals(listOf("a", "b"), QueueOrder.sorted(entries).map { it.id })
    }

    @Test
    fun `deplacer vers le haut reattribue les positions`() {
        val entries = listOf(entry("a", position = 0), entry("b", position = 1), entry("c", position = 2))
        val moved = QueueOrder.move(entries, "c", 0)
        assertEquals(listOf("c", "a", "b"), moved.map { it.id })
        assertEquals(listOf(0L, 1L, 2L), moved.map { it.position })
    }

    @Test
    fun `monter une entree deja en tete ne change rien`() {
        val entries = listOf(entry("a", position = 0), entry("b", position = 1))
        assertEquals(listOf("a", "b"), QueueOrder.bumpUp(entries, "a").map { it.id })
    }

    @Test
    fun `descendre la derniere entree ne change rien`() {
        val entries = listOf(entry("a", position = 0), entry("b", position = 1))
        assertEquals(listOf("a", "b"), QueueOrder.bumpDown(entries, "b").map { it.id })
    }

    @Test
    fun `un index hors bornes est ramene dans la liste`() {
        val entries = listOf(entry("a", position = 0), entry("b", position = 1))
        // 99 est ramené à la dernière place : « b » y est déjà, rien ne bouge.
        assertEquals(listOf("a", "b"), QueueOrder.move(entries, "b", 99).map { it.id })
        // -5 est ramené à la première place.
        assertEquals(listOf("b", "a"), QueueOrder.move(entries, "b", -5).map { it.id })
    }

    @Test
    fun `un identifiant inconnu renvoie la file inchangee`() {
        val entries = listOf(entry("a", position = 0))
        assertEquals(listOf("a"), QueueOrder.move(entries, "zzz", 0).map { it.id })
    }

    @Test
    fun `deux priorites differentes ne se melangent pas`() {
        val entries = listOf(
            entry("a", position = 0),
            entry("b", priority = 5, position = 1),
            entry("c", priority = 5, position = 2),
        )
        // « a » est seul dans sa bande de priorité : il ne peut pas franchir la frontière,
        // même en demandant à monter.
        val moved = QueueOrder.bumpUp(entries, "a")
        assertEquals(listOf("b", "c", "a"), moved.map { it.id })
        assertEquals(listOf("b", "c", "a"), QueueOrder.sorted(moved).map { it.id })

        // Dans une même bande, en revanche, le geste est respecté et persiste après tri.
        val withinBand = QueueOrder.bumpUp(entries, "c")
        assertEquals(listOf("c", "b", "a"), withinBand.map { it.id })
        assertEquals(listOf("c", "b", "a"), QueueOrder.sorted(withinBand).map { it.id })
    }
}
