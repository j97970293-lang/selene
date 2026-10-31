package io.selene.app.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Navigateur intégré dont le seul rôle est de **détecter les médias d'une page**.
 *
 * Il n'analyse pas le HTML et ne devine rien : il observe les requêtes que la page fait
 * réellement et retient celles qui pointent vers un fichier ou un manifeste. C'est ce que fait un
 * navigateur, et c'est vérifiable par l'utilisateur — le média proposé est un média que la page a
 * effectivement chargé.
 *
 * Les vidéos servies par un lecteur propriétaire (segmentées à la volée, chiffrées, ou assemblées
 * en JavaScript) n'apparaîtront pas : ce n'est pas un défaut à corriger, c'est la limite de
 * l'observation réseau.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(onPickMedia: (String) -> Unit) {
    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    val detected = remember { mutableStateListOf<String>() }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Adresse de la page") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(
            onClick = {
                val target = address.trim().let {
                    if (it.startsWith("http", ignoreCase = true)) it else "https://$it"
                }
                detected.clear()
                currentUrl = target
            },
            enabled = address.isNotBlank(),
        ) { Text("Ouvrir") }

        AndroidView(
            modifier = Modifier.fillMaxWidth().height(320.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            request.url.toString().takeIf(::looksLikeMedia)?.let { url ->
                                if (!detected.contains(url)) detected.add(url)
                            }
                            return null // aucune interception : la page se charge normalement
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            url?.let { address = it }
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            url?.let { address = it }
                        }
                    }
                    webView = this
                }
            },
            update = { view ->
                currentUrl?.let { url ->
                    if (view.url != url) view.loadUrl(url)
                }
            },
        )

        Text(
            if (detected.isEmpty()) {
                "Médias détectés : aucun pour l'instant. Lancez la lecture sur la page."
            } else {
                "Médias détectés : ${detected.size}"
            },
            style = MaterialTheme.typography.bodySmall,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(detected.toList(), key = { it }) { url ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(extensionOf(url), style = MaterialTheme.typography.titleSmall)
                            Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        TextButton(onClick = { onPickMedia(url) }) { Text("Télécharger") }
                    }
                }
            }
        }
    }

    // Le WebView doit être détruit explicitement, sinon il garde la page et l'audio en mémoire.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            webView?.loadUrl("about:blank")
            webView?.destroy()
            webView = null
        }
    }
}

private val MEDIA_EXTENSIONS = listOf(
    ".mp4", ".m4v", ".webm", ".mkv", ".mov",
    ".m4a", ".mp3", ".opus", ".ogg", ".flac",
    ".m3u8", ".mpd",
)

private fun looksLikeMedia(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return MEDIA_EXTENSIONS.any { path.endsWith(it) }
}

private fun extensionOf(url: String): String {
    val path = url.substringBefore('?').substringBefore('#')
    return path.substringAfterLast('.', "média").uppercase()
}
