package io.selene.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.selene.app.data.SettingsRepository
import io.selene.app.data.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val state = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settings.setWifiOnly(enabled) }

    fun setClipboardWatch(enabled: Boolean) =
        viewModelScope.launch { settings.setClipboardWatch(enabled) }

    fun setExternalResolver(enabled: Boolean, endpoint: String, token: String) =
        viewModelScope.launch { settings.setExternalResolver(enabled, endpoint, token) }

    fun setDownloadSlots(slots: Int) = viewModelScope.launch { settings.setDownloadSlots(slots) }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineSmall)

        SettingSwitch(
            title = "Télécharger uniquement en Wi-Fi",
            subtitle = "La file attend une connexion non facturée.",
            checked = state.wifiOnly,
            onCheckedChange = viewModel::setWifiOnly,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Téléchargements simultanés", style = MaterialTheme.typography.titleSmall)
            Text(
                "Au-delà de 2, le gain de débit est faible et le réseau mobile sature.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { slots ->
                    TextButton(onClick = { viewModel.setDownloadSlots(slots) }) {
                        Text(if (slots == state.downloadSlots) "[$slots]" else "$slots")
                    }
                }
            }
        }

        SettingSwitch(
            title = "Proposer les liens copiés",
            subtitle = "Désactivé par défaut : Selene ne lit pas le presse-papiers sans votre accord.",
            checked = state.clipboardWatch,
            onCheckedChange = viewModel::setClipboardWatch,
        )

        ExternalResolverSection(
            enabled = state.externalResolverEnabled,
            endpoint = state.externalResolverEndpoint,
            token = state.externalResolverToken,
            onSave = viewModel::setExternalResolver,
        )

        Column {
            Text("Stockage", style = MaterialTheme.typography.titleSmall)
            Text(
                "Les fichiers sont écrits dans le dossier privé de l'application puis partageables. " +
                    "L'export vers le dossier Téléchargements viendra dans une prochaine version.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Column {
            Text("À propos", style = MaterialTheme.typography.titleSmall)
            Text(
                "Selene 0.1.0 — application originale, reconstruite proprement. " +
                    "Aucun code, aucune ressource et aucune marque d'une application tierce n'est utilisée.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Pont d'extraction auto-hébergé. Désactivé par défaut ; le bouton n'enregistre que si une
 * adresse et un jeton sont saisis, pour qu'une activation par inadvertance n'envoie rien.
 */
@Composable
private fun ExternalResolverSection(
    enabled: Boolean,
    endpoint: String,
    token: String,
    onSave: (Boolean, String, String) -> Unit,
) {
    var draftEndpoint by remember { mutableStateOf(endpoint) }
    var draftToken by remember { mutableStateOf(token) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Source externe (optionnel)", style = MaterialTheme.typography.titleSmall)
        Text(
            "Selene peut interroger un service que vous hébergez vous-même et qui lui rend des " +
                "adresses de fichiers. Rien n'est envoyé sans cette activation. Format attendu : " +
                "schéma selene.external/1.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = draftEndpoint,
            onValueChange = { draftEndpoint = it },
            label = { Text("Adresse du service (https://…)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = draftToken,
            onValueChange = { draftToken = it },
            label = { Text("Jeton (Bearer)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(
                checked = enabled,
                onCheckedChange = { onSave(it, draftEndpoint, draftToken) },
            )
            Text(if (enabled) "Activée" else "Désactivée", style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = { onSave(true, draftEndpoint, draftToken) },
            enabled = draftEndpoint.isNotBlank() && draftToken.isNotBlank(),
        ) {
            Text("Enregistrer l'adresse et le jeton")
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
