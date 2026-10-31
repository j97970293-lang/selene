package io.selene.app.ui.browser

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.selene.app.browser.DetectedKind
import io.selene.app.browser.DetectedMedia
import io.selene.app.browser.MediaUrlClassifier
import org.json.JSONArray

/**
 * Navigateur intégré dont le seul rôle est de **détecter les médias d'une page**.
 *
 * Trois sources, toutes des observations, aucune supposition :
 *  1. les requêtes que la page émet (`shouldInterceptRequest`, `onLoadResource`) ;
 *  2. ce que la page affiche réellement dans ses balises `<video>`/`<audio>` ;
 *  3. les liens explicites vers un fichier.
 *
 * Les vidéos assemblées à la volée (`blob:`) ou chiffrées (DRM) n'ont pas
 * d'adresse récupérable : elles sont signalées comme telles plutôt que masquées,
 * pour que l'utilisateur sache que ce n'est pas un défaut de réglage.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(onPickMedia: (String) -> Unit) {
    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    val detected = remember { mutableStateListOf<DetectedMedia>() }
    val clipboard = LocalClipboardManager.current

    fun offer(url: String) {
        val media = MediaUrlClassifier.detect(url) ?: return
        if (detected.none { it.key == media.key }) detected.add(media)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Adresse de la page") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    val target = address.trim().let {
                        if (it.startsWith("http", ignoreCase = true)) it else "https://$it"
                    }
                    detected.clear()
                    pageTitle = null
                    currentUrl = target
                },
                enabled = address.isNotBlank(),
            ) { Text("Ouvrir") }

            if (detected.isNotEmpty()) {
                TextButton(onClick = { detected.clear() }) { Text("Vider la liste") }
            }
        }

        if (currentUrl == null) {
            Text(
                "Ouvrez une page : Séléné retient les médias qu'elle charge réellement. " +
                    "Aucune analyse du HTML, aucune interception : l'observation seule.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            pageTitle?.let { Text(it, style = MaterialTheme.typography.titleSmall) }

            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                // Observer ne veut pas dire bloquer : la requête part
                                // normalement, on note seulement l'adresse si elle est un média.
                                offer(request.url.toString())
                                return null
                            }

                            override fun onLoadResource(view: WebView, url: String) {
                                offer(url)
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                view.title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                                // Deuxième passage : ce que la page a réellement mis dans
                                // ses balises, y compris les adresses relatives.
                                view.evaluateJavascript(PROBE_SCRIPT) { result ->
                                    collectFromProbe(result).forEach(::offer)
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                            }
                        }

                        loadUrl(currentUrl!!)
                    }
                },
                update = { view ->
                    val target = currentUrl
                    if (target != null && view.url != target) view.loadUrl(target)
                },
            )

            Text(
                "Médias détectés : ${detected.size}",
                style = MaterialTheme.typography.titleSmall,
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(detected, key = { it.key }) { media ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "${labelOf(media.kind)} — ${shortUrl(media.url)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { onPickMedia(media.url) },
                                    enabled = media.kind.isResolvable,
                                ) { Text("Analyser") }

                                TextButton(
                                    onClick = { clipboard.setText(AnnotatedString(media.url)) },
                                ) { Text("Copier") }
                            }
                            if (!media.kind.isResolvable) {
                                Text(
                                    when (media.kind) {
                                        DetectedKind.SEGMENT ->
                                            "Fragment isolé : la page assemble le média elle-même, " +
                                                "ce fichier seul ne se lit pas."

                                        DetectedKind.OPAQUE ->
                                            "Adresse blob: — le média n'existe pas comme fichier " +
                                                "téléchargeable (assemblage en mémoire ou chiffrement)."

                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DetectedKind.isResolvable: Boolean
    get() = this == DetectedKind.MANIFEST || this == DetectedKind.VIDEO ||
        this == DetectedKind.AUDIO || this == DetectedKind.SUBTITLE

private fun labelOf(kind: DetectedKind): String = when (kind) {
    DetectedKind.MANIFEST -> "Flux"
    DetectedKind.VIDEO -> "Vidéo"
    DetectedKind.AUDIO -> "Audio"
    DetectedKind.SUBTITLE -> "Sous-titres"
    DetectedKind.SEGMENT -> "Fragment"
    DetectedKind.OPAQUE -> "Protégé"
}

private fun shortUrl(url: String): String =
    if (url.length <= 70) url else url.take(40) + "…" + url.takeLast(27)

/**
 * Script d'observation, exécuté une fois la page chargée. Il ne modifie rien et
 * ne contourne rien : il lit `currentSrc` et les `source`, puis renvoie un tableau.
 */
private val PROBE_SCRIPT = """
(function () {
  var out = [];
  try {
    var nodes = document.querySelectorAll('video, audio');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.currentSrc) out.push([el.currentSrc]);
      if (el.src) out.push([el.src]);
      var sources = el.querySelectorAll('source');
      for (var j = 0; j < sources.length; j++) if (sources[j].src) out.push([sources[j].src]);
    }
    var links = document.querySelectorAll('a[href]');
    for (var k = 0; k < links.length; k++) {
      var href = links[k].href;
      if (!href) continue;
      if (/\.(mp4|m4v|webm|mkv|mov|mp3|m4a|opus|ogg|flac|m3u8|mpd)(\?|#|${'$'})/i.test(href)) out.push([href]);
    }
  } catch (e) { }
  return out;
})()
"""

/** Lit le tableau renvoyé par [PROBE_SCRIPT] ; une réponse illisible est ignorée. */
private fun collectFromProbe(raw: String?): List<String> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val entry = array.optJSONArray(index) ?: continue
                entry.optString(0).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
}
