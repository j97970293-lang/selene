package io.selene.app.data

/**
 * Réglages utilisateur — Kotlin pur, sans dépendance Android.
 *
 * Séparés du dépôt DataStore pour que les classes qui les consomment (résolveur externe,
 * file de téléchargement) restent compilables et testables hors appareil.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val wifiOnly: Boolean = false,
    /** Détection du presse-papiers : opt-in explicite, désactivée par défaut. */
    val clipboardWatch: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val destinationFolderId: String? = null,
    /** Pont d'extraction auto-hébergé — désactivé par défaut (docs/EXTERNAL-RESOLVER.md). */
    val externalResolverEnabled: Boolean = false,
    val externalResolverEndpoint: String = "",
    val externalResolverToken: String = "",
)
