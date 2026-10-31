package io.selene.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.selene.app.data.SettingsRepository
import io.selene.app.data.UserSettings
import io.selene.app.downloading.YtDlpEnvironment
import io.selene.app.resolver.BridgeHealth
import io.selene.app.resolver.BridgeStatus
import io.selene.app.resolver.ExternalResolver
import io.selene.app.ui.permission.openNotificationSettings
import io.selene.app.ui.permission.rememberNotificationsEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État du moteur yt-dlp tel que l'écran peut le montrer. */
data class EngineState(
    val checking: Boolean = false,
    val version: String? = null,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val ytDlp: YtDlpEnvironment,
    private val bridge: ExternalResolver,
) : ViewModel() {

    val state = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _engine = MutableStateFlow(EngineState())
    val engine: StateFlow<EngineState> = _engine.asStateFlow()

    private val _bridgeStatus = MutableStateFlow<BridgeStatus?>(null)
    val bridgeStatus: StateFlow<BridgeStatus?> = _bridgeStatus.asStateFlow()

    private val _bridgeChecking = MutableStateFlow(false)
    val bridgeChecking: StateFlow<Boolean> = _bridgeChecking.asStateFlow()

    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch { settings.setWifiOnly(enabled) }

    fun setClipboardWatch(enabled: Boolean) =
        viewModelScope.launch { settings.setClipboardWatch(enabled) }

    fun setExportToGallery(enabled: Boolean) =
        viewModelScope.launch { settings.setExportToGallery(enabled) }

    fun setExternalResolver(enabled: Boolean, endpoint: String, token: String) =
        viewModelScope.launch {
            settings.setExternalResolver(enabled, endpoint, token)
            // L'ancien diagnostic ne décrit plus la configuration enregistrée.
            _bridgeStatus.value = null
        }

    fun setDownloadSlots(slots: Int) = viewModelScope.launch { settings.setDownloadSlots(slots) }

    fun checkEngine() = viewModelScope.launch {
        _engine.update { it.copy(checking = true, message = null) }
        val version = ytDlp.version()
        _engine.value = EngineState(
            checking = false,
            version = version,
            message = if (version == null) {
                ytDlp.errorOrNull() ?: "moteur yt-dlp indisponible sur cet appareil"
            } else {
                null
            },
        )
    }

    fun updateEngine() = viewModelScope.launch {
        _engine.update { it.copy(checking = true, message = null) }
        ytDlp.update()
            .onSuccess { message ->
                _engine.value = EngineState(checking = false, version = ytDlp.version(), message = message)
            }
            .onFailure { error ->
                _engine.value = EngineState(
                    checking = false,
                    version = ytDlp.version(),
                    message = error.message ?: "mise à jour impossible",
                )
            }
    }

    fun testBridge() = viewModelScope.launch {
        _bridgeChecking.value = true
        _bridgeStatus.value = bridge.healthCheck()
        _bridgeChecking.value = false
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val bridgeStatus by viewModel.bridgeStatus.collectAsStateWithLifecycle()
    val bridgeChecking by viewModel.bridgeChecking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationsOn = rememberNotificationsEnabled()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
            title = "Copier dans la galerie",
            subtitle = "Une copie est déposée dans Movies/Selene, Music/Selene ou " +
                "Download/Selene à la fin du téléchargement. Le fichier reste aussi dans Séléné.",
            checked = state.exportToGallery,
            onCheckedChange = viewModel::setExportToGallery,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Notifications", style = MaterialTheme.typography.titleSmall)
            Text(
                if (notificationsOn) {
                    "Activées : progression et fin de téléchargement sont visibles."
                } else {
                    "Désactivées : les téléchargements se font, mais sans aucune indication " +
                        "dans la barre d'état."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (!notificationsOn) {
                TextButton(onClick = { openNotificationSettings(context) }) {
                    Text("Ouvrir les réglages de notifications")
                }
            }
        }

        SettingSwitch(
            title = "Proposer les liens copiés",
            subtitle = "Désactivé par défaut : Séléné ne lit pas le presse-papiers sans votre accord.",
            checked = state.clipboardWatch,
            onCheckedChange = viewModel::setClipboardWatch,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Moteur yt-dlp", style = MaterialTheme.typography.titleSmall)
            Text(
                "Lit les pages des sites que yt-dlp prend en charge. Son binaire peut être mis à " +
                    "jour sans réinstaller l'application, quand un site change.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                when {
                    engine.checking -> "Vérification…"
                    engine.version != null -> "Version installée : ${engine.version}"
                    else -> "État inconnu — appuyer sur Vérifier."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            engine.message?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::checkEngine, enabled = !engine.checking) {
                    Text("Vérifier")
                }
                TextButton(onClick = viewModel::updateEngine, enabled = !engine.checking) {
                    Text("Mettre à jour")
                }
            }
        }

        ExternalResolverSection(
            enabled = state.externalResolverEnabled,
            endpoint = state.externalResolverEndpoint,
            token = state.externalResolverToken,
            status = bridgeStatus,
            checking = bridgeChecking,
            onSave = viewModel::setExternalResolver,
            onTest = viewModel::testBridge,
        )

        Column {
            Text("Stockage", style = MaterialTheme.typography.titleSmall)
            Text(
                "Les fichiers sont écrits dans le dossier privé de l'application, puis copiés " +
                    "dans le stockage partagé si l'option ci-dessus est active. La copie ne " +
                    "remplace pas l'original : effacer l'un n'efface pas l'autre.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Column {
            Text("À propos", style = MaterialTheme.typography.titleSmall)
            Text(
                "Séléné 0.2.0 — application originale, reconstruite proprement. " +
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
    status: BridgeStatus?,
    checking: Boolean,
    onSave: (Boolean, String, String) -> Unit,
    onTest: () -> Unit,
) {
    var draftEndpoint by remember { mutableStateOf(endpoint) }
    var draftToken by remember { mutableStateOf(token) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Source externe (optionnel)", style = MaterialTheme.typography.titleSmall)
        Text(
            "Séléné peut interroger un service que vous hébergez vous-même et qui lui rend des " +
                "adresses de fichiers. C'est la seule voie pour les sites que Séléné ne lit pas " +
                "lui-même : le service est à vous, l'application ne décode aucune page. " +
                "Contrat attendu : schéma selene.external/1.",
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onTest,
                enabled = !checking && draftEndpoint.isNotBlank() && draftToken.isNotBlank(),
            ) {
                Text("Tester la connexion")
            }
            if (checking) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        }

        status?.let { current ->
            val failed = current !is BridgeStatus.Ok
            Text(
                BridgeHealth.describe(current),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
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
