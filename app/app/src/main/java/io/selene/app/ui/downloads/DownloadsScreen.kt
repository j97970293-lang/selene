package io.selene.app.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.selene.app.data.SettingsRepository
import io.selene.app.data.db.TaskRow
import io.selene.app.data.db.TaskDao
import io.selene.app.downloading.DownloadRepository
import io.selene.app.downloading.ExportResult
import io.selene.app.downloading.MediaExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val gallery: MediaExporter,
    private val taskDao: TaskDao,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Réexporte un fichier terminé vers le stockage partagé. Utile quand l'export
     * automatique a échoué (espace, permission) ou quand l'option était désactivée.
     */
    fun exportToGallery(task: TaskRow) = viewModelScope.launch {
        val path = task.destinationUri?.let { android.net.Uri.parse(it).path }
        val file = path?.let { File(it) }
        if (file == null || !file.isFile) {
            _message.value = "Fichier introuvable : il a peut-être été déplacé ou supprimé."
            return@launch
        }
        when (val result = gallery.export(file)) {
            is ExportResult.Saved -> {
                taskDao.setExported(task.id, result.uri.toString(), System.currentTimeMillis())
                _message.value = "Copie enregistrée dans ${result.path}"
            }
            ExportResult.PermissionRequired ->
                _message.value = "Android 9 et antérieurs : autorisez le stockage depuis l'écran Bibliothèque."
            is ExportResult.Failed -> _message.value = result.message
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    val tasks = downloads.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pause(task: TaskRow) = viewModelScope.launch { downloads.pause(task.id) }

    fun resume(task: TaskRow) = viewModelScope.launch {
        val wifiOnly = settings.settings.first().wifiOnly
        downloads.resume(task.id, wifiOnly)
    }

    fun cancel(task: TaskRow) = viewModelScope.launch { downloads.cancel(task.id) }

    fun remove(task: TaskRow) = viewModelScope.launch { downloads.deleteRecord(task.id) }

    fun moveUp(task: TaskRow) = viewModelScope.launch { downloads.move(task.id, -1) }

    fun moveDown(task: TaskRow) = viewModelScope.launch { downloads.move(task.id, 1) }

    fun boost(task: TaskRow) =
        viewModelScope.launch { downloads.setPriority(task.id, if (task.priority > 0) 0 else 10) }
}

private val activeStates = setOf("QUEUED", "RUNNING", "PAUSED")

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onPlay: (String) -> Unit,
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val active = tasks.filter { it.state in activeStates }
    val finished = tasks.filter { it.state !in activeStates }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        message?.let { text ->
            item {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item { Text("En cours", style = MaterialTheme.typography.titleMedium) }

        if (active.isEmpty()) {
            item { Text("Aucun téléchargement en cours.", style = MaterialTheme.typography.bodySmall) }
        }

        items(active, key = { it.id }) { task ->
            TaskCard(
                task = task,
                onPause = { viewModel.pause(task) },
                onResume = { viewModel.resume(task) },
                onCancel = { viewModel.cancel(task) },
                onPlay = null,
                onRemove = null,
            )
        }

        item { Text("Terminés", style = MaterialTheme.typography.titleMedium) }

        if (finished.isEmpty()) {
            item { Text("Rien pour l'instant.", style = MaterialTheme.typography.bodySmall) }
        }

        items(finished, key = { it.id }) { task ->
            TaskCard(
                task = task,
                onPause = null,
                onResume = { viewModel.resume(task) },
                onCancel = null,
                onPlay = task.destinationUri?.let { uri -> { onPlay(uri) } },
                onRemove = { viewModel.remove(task) },
                onExport = { viewModel.exportToGallery(task) },
                onUp = { viewModel.moveUp(task) },
                onDown = { viewModel.moveDown(task) },
                onBoost = { viewModel.boost(task) },
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskRow,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onBoost: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(task.qualityLabel, task.container.uppercase(), task.state).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )

            task.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }

            if (task.exportUri != null) {
                Text("Copie dans la galerie", style = MaterialTheme.typography.labelSmall)
            }

            task.lastError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onPlay?.let { TextButton(onClick = it) { Text("Ouvrir") } }
                onPause?.let { TextButton(onClick = it) { Text("Pause") } }
                onResume?.let { TextButton(onClick = it) { Text("Reprendre") } }
                onCancel?.let { TextButton(onClick = it) { Text("Annuler") } }
                onRemove?.let { TextButton(onClick = it) { Text("Retirer") } }
                if (onExport != null && task.exportUri == null) {
                    TextButton(onClick = onExport) { Text("Vers la galerie") }
                }
            }

            // File priorisée : ▲ / ▼ dans la même bande, ★ pour passer en urgent (priorité 10).
            if (task.state == "QUEUED" || task.state == "PAUSED") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    onUp?.let { TextButton(onClick = it) { Text("▲") } }
                    onDown?.let { TextButton(onClick = it) { Text("▼") } }
                    onBoost?.let {
                        TextButton(onClick = it) {
                            Text(if (task.priority > 0) "★ urgent" else "☆ prioriser")
                        }
                    }
                }
            }
        }
    }
}
