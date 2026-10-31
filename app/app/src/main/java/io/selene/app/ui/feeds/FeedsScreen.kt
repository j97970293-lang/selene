package io.selene.app.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.selene.app.data.SettingsRepository
import io.selene.app.downloading.DownloadRepository
import io.selene.app.model.ResolvedMedia
import io.selene.app.model.Variant
import io.selene.app.resolver.ResolverRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Écran des flux : un podcast s'affiche comme une liste d'épisodes, pas comme un sélecteur de
 * qualité. C'est le modèle mental attendu, et il évite le détour par l'écran de résolution.
 */
data class FeedsUiState(
    val url: String = "",
    val loading: Boolean = false,
    val media: ResolvedMedia? = null,
    val error: String? = null,
    val lastEnqueued: String? = null,
)

@HiltViewModel
class FeedsViewModel @Inject constructor(
    private val resolvers: ResolverRegistry,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedsUiState())
    val state: StateFlow<FeedsUiState> = _state.asStateFlow()

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, error = null) }

    fun load() {
        val url = _state.value.url.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, media = null, lastEnqueued = null) }
            runCatching { resolvers.resolve(url) }
                .onSuccess { media -> _state.update { it.copy(loading = false, media = media) } }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.message ?: "flux illisible") }
                }
        }
    }

    fun download(variant: Variant) {
        val media = _state.value.media ?: return
        viewModelScope.launch {
            val wifiOnly = settings.settings.first().wifiOnly
            downloads.enqueue(media, variant, wifiOnly)
            _state.update { it.copy(lastEnqueued = variant.qualityLabel ?: "épisode") }
        }
    }
}

@Composable
fun FeedsScreen(viewModel: FeedsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Flux et podcasts", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Collez l'adresse d'un flux RSS ou Atom : Selene liste les épisodes téléchargeables.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = state.url,
            onValueChange = viewModel::onUrlChange,
            label = { Text("https://exemple.tld/feed.xml") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::load, enabled = !state.loading && state.url.isNotBlank()) {
                Text("Charger le flux")
            }
        }

        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
        }

        state.lastEnqueued?.let {
            Text("« $it » ajouté à la file.", style = MaterialTheme.typography.bodySmall)
        }

        state.media?.let { media ->
            Text(media.title, style = MaterialTheme.typography.titleMedium)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(media.variants, key = { it.id }) { variant ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    variant.qualityLabel ?: "Épisode",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    listOfNotNull(
                                        variant.container.uppercase(),
                                        variant.sizeBytes?.let { "${it / 1_048_576} Mo" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { viewModel.download(variant) }) { Text("Télécharger") }
                        }
                    }
                }
            }
        }
    }
}
