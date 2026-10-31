package io.selene.app.resolver

import io.selene.app.model.ResolvedMedia
import kotlinx.coroutines.CancellationException

/**
 * Point d'extension unique pour l'obtention d'un média.
 *
 * Le reste de l'application ne sait pas d'où vient le flux : tout passe par cette interface.
 * Les implémentations couvrant une plateforme tierce en contournement de ses conditions
 * d'utilisation ne font pas partie de ce projet (voir RULES.md).
 */
interface MediaResolver {
    /** Identifiant stable, persisté avec le média. */
    val id: String

    /**
     * Priorité de sélection. Le résolveur le plus prioritaire est essayé en premier :
     * un manifeste HLS doit l'emporter sur le résolveur direct, qui accepte tout `http(s)`.
     */
    val priority: Int get() = 0

    /** Indique si ce résolveur sait traiter cette URL. Ne doit pas lever d'exception. */
    fun matches(url: String): Boolean

    /** Résout l'URL en un média et ses variantes. Peut lancer un appel réseau. */
    suspend fun resolve(url: String): ResolvedMedia
}

class NoResolverException(url: String) :
    IllegalArgumentException("Aucune source ne sait traiter cette adresse : $url")

class ResolverRegistry(
    resolvers: Set<MediaResolver>,
) {
    // Les résolveurs les plus spécifiques d'abord, à priorité égale ordre alphabétique stable.
    private val ordered: List<MediaResolver> =
        resolvers.sortedWith(compareByDescending<MediaResolver> { it.priority }.thenBy { it.id })

    fun pick(url: String): MediaResolver? = ordered.firstOrNull { it.matches(url) }

    fun candidates(url: String): List<MediaResolver> = ordered.filter { it.matches(url) }

    /**
     * Essaie les résolveurs dans l'ordre de priorité et retient le premier qui répond.
     *
     * L'échec d'un résolveur ne condamne pas l'adresse : un serveur qui refuse une requête `HEAD`
     * reste téléchargeable, et c'est exactement le cas où le pont externe prend le relais. On
     * relance l'erreur du résolveur le plus prioritaire, la plus précise pour l'utilisateur.
     */
    suspend fun resolve(url: String): ResolvedMedia {
        val normalized = url.trim()
        val resolvers = candidates(normalized)
        if (resolvers.isEmpty()) throw NoResolverException(normalized)

        var firstError: Exception? = null
        for (resolver in resolvers) {
            try {
                return resolver.resolve(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (firstError == null) firstError = error
            }
        }
        throw firstError ?: NoResolverException(normalized)
    }
}
