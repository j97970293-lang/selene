package io.selene.app.ui.resolve

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.selene.app.data.SettingsRepository
import io.selene.app.downloading.DownloadRepository
import io.selene.app.model.ResolvedMedia
import io.selene.app.model.Variant
import io.selene.app.imports.YtDlpJson
import io.selene.app.resolver.ResolverRegistry
import io.selene.app.ui.handoff.Handoff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResolveUiState(
    val url: String = "",
    val loading: Boolean = false,
    val media: ResolvedMedia? = null,
    val selectedVariantId: String? = null,
    val error: String? = null,
    val enqueued: Boolean = false,
) {
    val selectedVariant: Variant?
        get() = media?.variants?.firstOrNull { it.id == selectedVariantId }
}

@HiltViewModel
class ResolveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resolvers: ResolverRegistry,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ResolveUiState(url = savedStateHandle.get<String>("url").orEmpty()),
    )
    val state: StateFlow<ResolveUiState> = _state.asStateFlow()

    /**
     * Lien trouvé dans le presse-papiers. Le réglage « Proposer les liens copiés »
     * existait dans l'écran de réglages sans être lu nulle part : il est désormais
     * appliqué ici, et seulement s'il est activé.
     */
    private val _suggestion = MutableStateFlow<String?>(null)
    val suggestion: StateFlow<String?> = _suggestion.asStateFlow()

    private val _clipboardWatch = MutableStateFlow(false)
    val clipboardWatch: StateFlow<Boolean> = _clipboardWatch.asStateFlow()

    init {
        viewModelScope.launch {
            _clipboardWatch.value = settings.settings.first().clipboardWatch
        }
        if (_state.value.url.isNotBlank()) analyze()
    }

    /** Appelé par l'écran avec le contenu du presse-papiers, quand le réglage est actif. */
    fun offerFromClipboard(candidate: String?) {
        val url = candidate?.let { text ->
            Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', ']')
        } ?: return
        if (url == _state.value.url.trim() || url == _suggestion.value) return
        _suggestion.value = url
    }

    fun acceptSuggestion() {
        val url = _suggestion.value ?: return
        _suggestion.value = null
        _state.update { it.copy(url = url, error = null) }
        analyze()
    }

    fun dismissSuggestion() {
        _suggestion.value = null
    }

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, error = null) }

    fun analyze() {
        val url = _state.value.url.trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            _state.update { it.copy(error = "invalid-url") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, media = null, enqueued = false) }
            runCatching { resolvers.resolve(url) }
                .onSuccess { media ->
                    _state.update {
                        it.copy(
                            loading = false,
                            media = media,
                            selectedVariantId = media.variants.firstOrNull()?.id,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, error = error.message ?: "error")
                    }
                }
        }
    }

    /**
     * Importe une sortie `yt-dlp -J` déjà produite par l'utilisateur. L'extraction a eu lieu
     * dehors, avec son outil ; Selene ne fait ici que lire un JSON public et le transformer en
     * variantes téléchargeables par son propre moteur.
     */
    fun importJson(json: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, media = null, enqueued = false) }
            runCatching { withContext(Dispatchers.Default) { YtDlpJson.parse(json) } }
                .onSuccess { media ->
                    _state.update {
                        it.copy(
                            url = media.canonicalUrl,
                            loading = false,
                            media = media,
                            selectedVariantId = media.variants.firstOrNull()?.id,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message ?: "import impossible") }
                }
        }
    }

    fun selectVariant(id: String) = _state.update { it.copy(selectedVariantId = id) }

    fun download() {
        val media = _state.value.media ?: return
        val variant = _state.value.selectedVariant ?: return
        viewModelScope.launch {
            val wifiOnly = settings.settings.first().wifiOnly
            downloads.enqueue(media, variant, wifiOnly)
            _state.update { it.copy(enqueued = true) }
        }
    }
}

@Composable
fun ResolveScreen(
    viewModel: ResolveViewModel,
    onOpenDownloads: () -> Unit,
    onOpenBrowser: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val clipboardWatch by viewModel.clipboardWatch.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Lecture du presse-papiers uniquement si l'utilisateur l'a autorisée, et
    // seulement quand le champ est vide : rien n'est écrasé à son insu.
    LaunchedEffect(clipboardWatch, state.url) {
        if (!clipboardWatch || state.url.isNotBlank()) return@LaunchedEffect
        viewModel.offerFromClipboard(clipboard.getText()?.text?.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nouveau téléchargement", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = state.url,
            onValueChange = viewModel::onUrlChange,
            label = { Text("Adresse du média") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        suggestion?.let { url ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Lien copié : ${if (url.length > 48) url.take(45) + "…" else url}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                TextButton(onClick = viewModel::acceptSuggestion) { Text("Utiliser") }
                TextButton(onClick = viewModel::dismissSuggestion) { Text("Ignorer") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::analyze,
                enabled = !state.loading && state.url.isNotBlank(),
            ) { Text("Analyser") }

            if (state.enqueued) {
                TextButton(onClick = onOpenDownloads) { Text("Voir les téléchargements") }
            }
        }

        // Import d'une sortie `yt-dlp -J` produite ailleurs : aucun décodeur de page ici, juste
        // un format public que l'utilisateur a généré avec l'outil de son choix.
        val scope = rememberCoroutineScope()
        val pickJson = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        }.getOrNull()
                    }
                    viewModel.importJson(text ?: "{}")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { pickJson.launch(arrayOf("application/json", "text/plain")) }) {
                Text("Importer une sortie yt-dlp (.json)")
            }
            TextButton(onClick = onOpenBrowser) { Text("Parcourir une page") }
        }

        when {
            state.loading -> CircularProgressIndicator()
            state.error == "invalid-url" -> Text(
                "Adresse invalide : une URL http(s) complète est attendue.",
                color = MaterialTheme.colorScheme.error,
            )

            state.error != null -> {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                // Délégation assumée : la page n'est pas décodée ici, mais l'utilisateur peut
                // l'envoyer à une application qu'il a choisie et installée lui-même.
                val intent = Handoff.shareIntent(context, state.url)
                if (intent != null) {
                    TextButton(onClick = { context.startActivity(intent) }) {
                        Text("Ouvrir l'adresse avec une autre application")
                    }
                }
            }
        }

        val media = state.media
        if (media != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(media.title, style = MaterialTheme.typography.titleMedium)
                    media.author?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("Choisir un format", style = MaterialTheme.typography.titleSmall)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(media.variants, key = { it.id }) { variant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = variant.id == state.selectedVariantId,
                                onClick = { viewModel.selectVariant(variant.id) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = variant.id == state.selectedVariantId,
                            onClick = { viewModel.selectVariant(variant.id) },
                        )
                        Text(variant.displayLabel, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            if (media.variants.isEmpty()) {
                Text("Aucun format exploitable trouvé pour cette adresse.")
            } else {
                Button(
                    onClick = viewModel::download,
                    enabled = state.selectedVariant != null && !state.enqueued,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Télécharger") }
            }
        }
    }
}
