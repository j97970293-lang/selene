package io.selene.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.selene.app.ui.permission.RequestNotificationPermissionOnce
import io.selene.app.ui.theme.SeleneTheme
import io.selene.app.ui.navigation.SeleneNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val incomingUrl = extractUrl(intent)
        setContent {
            SeleneTheme {
                // Demande unique au premier lancement : sans elle, aucune notification
                // de téléchargement n'apparaît, ni progression ni fin.
                RequestNotificationPermissionOnce()
                SeleneNavHost(initialUrl = incomingUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // La navigation vers l'écran de résolution est prise en charge par
        // l'état initial ; une activité déjà ouverte relance via recreate().
        extractUrl(intent)?.let { recreate() }
    }

    /** URL partagée (ACTION_SEND) ou lien selene://resolve?url=… (ACTION_VIEW). */
    private fun extractUrl(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let(::firstHttpUrl)

            Intent.ACTION_VIEW -> intent.dataString
                ?.takeIf { it.startsWith("http", ignoreCase = true) }
                ?: intent.data?.getQueryParameter("url")

            else -> null
        }
    }

    private fun firstHttpUrl(text: String): String? =
        Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', ']')
}
