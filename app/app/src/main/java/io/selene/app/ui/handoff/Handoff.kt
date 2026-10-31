package io.selene.app.ui.handoff

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Délégation vers une application installée qui sait traiter une adresse que Selene refuse.
 *
 * Pourquoi cette classe plutôt que du code d'extraction : l'utilisateur peut avoir installé un
 * outil qui, lui, décode les pages de plateformes, et il l'a choisi. Selene ne l'embarque pas, ne
 * le modifie pas, ne le contourne pas : il lui passe l'adresse, une fois, sur un geste explicite.
 * Si aucune de ces applications n'est présente, le sélecteur système prend le relais.
 *
 * Aucune donnée n'est transmise en tâche de fond : seuls les `Intent` ACTION_SEND déclenchés par
 * l'utilisateur sortent de l'application.
 */
object Handoff {

    /** Applications connues pour traiter des adresses de pages. Ordre = préférence. */
    private val KNOWN_PACKAGES = listOf(
        "com.junkfood.seal",           // Seal
        "org.schabi.newpipe",          // NewPipe
        "org.polymorphicshade.tubular", // Tubular
        "org.schabi.newpipelegacy",
        "com.github.libretube",        // LibreTube
    )

    /** Renvoie les applications installées capables de recevoir une adresse en partage. */
    fun installedTargets(context: Context): List<String> = KNOWN_PACKAGES.filter { pkg ->
        runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * Intent de partage, ciblé sur [packageName] s'il est fourni et installé, sinon sélecteur
     * système. Renvoie null si aucune application ne peut recevoir de texte.
     */
    fun shareIntent(context: Context, url: String, packageName: String? = null): Intent? {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val resolved = packageName?.let { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                Intent(send).setPackage(pkg)
            }.getOrNull()
        }

        return when {
            resolved != null && canHandle(context, resolved) -> resolved
            canHandle(context, send) -> Intent.createChooser(send, "Ouvrir l'adresse avec…")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            else -> null
        }
    }

    private fun canHandle(context: Context, intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()
}
