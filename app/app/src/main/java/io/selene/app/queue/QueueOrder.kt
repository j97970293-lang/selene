package io.selene.app.queue

/**
 * Ordre de la file de téléchargements — logique pure, sans Room ni Android, donc testable JVM.
 *
 * Deux critères, dans cet ordre : la **priorité** (un téléchargement urgent double la file), puis
 * la **position** (l'ordre choisi par l'utilisateur à l'intérieur d'un même niveau).
 */
data class QueueEntry(
    val id: String,
    val priority: Int,
    val position: Long,
)

/** Projection Room de la file active (un constructeur par défaut est requis par Room). */
data class QueueRow(
    val id: String,
    val priority: Int,
    val position: Long,
)

object QueueOrder {

    val comparator: Comparator<QueueEntry> =
        compareByDescending<QueueEntry> { it.priority }.thenBy { it.position }.thenBy { it.id }

    fun sorted(entries: List<QueueEntry>): List<QueueEntry> = entries.sortedWith(comparator)

    /**
     * Déplace une entrée à l'index demandé et réattribue les positions de 0 à n-1.
     *
     * On réattribue tout plutôt que d'écrire des positions fractionnaires : avec quelques
     * dizaines d'entrées, une réécriture complète est moins coûteuse en bugs qu'un intervalle
     * flottant qui finit par s'épuiser.
     *
     * @return la liste complète des entrées avec leurs nouvelles positions, dans l'ordre final.
     */
    fun move(entries: List<QueueEntry>, id: String, toIndex: Int): List<QueueEntry> {
        val ordered = sorted(entries).toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        if (from < 0) return ordered

        // On ne franchit jamais une frontière de priorité : sinon la position écrite serait
        // aussitôt démentie par le tri, et la file semblerait ignorer le geste de l'utilisateur.
        val priority = ordered[from].priority
        val band = ordered.indices.filter { ordered[it].priority == priority }
        val target = toIndex.coerceIn(band.first(), band.last())
        if (from == target) return ordered

        val moved = ordered.removeAt(from)
        ordered.add(target, moved)
        return ordered.mapIndexed { index, entry -> entry.copy(position = index.toLong()) }
    }

    /** Monte d'un cran. Au sommet de sa priorité, l'entrée ne bouge pas. */
    fun bumpUp(entries: List<QueueEntry>, id: String): List<QueueEntry> =
        move(entries, id, sorted(entries).indexOfFirst { it.id == id } - 1)

    /** Descend d'un cran. */
    fun bumpDown(entries: List<QueueEntry>, id: String): List<QueueEntry> =
        move(entries, id, sorted(entries).indexOfFirst { it.id == id } + 1)
}
