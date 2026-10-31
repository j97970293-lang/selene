package io.selene.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.selene.app.data.db.MediaDao
import io.selene.app.converting.AudioExporter
import io.selene.app.data.db.MediaItemEntity
import io.selene.app.data.db.TaskDao
import io.selene.app.vault.VaultManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val taskDao: TaskDao,
    private val exporter: AudioExporter,
    private val vault: VaultManager,
) : ViewModel() {

    /** Message court d'une opération ponctuelle (conversion, coffre). */
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val items = _query
        .flatMapLatest { query -> mediaDao.observeSearch(query.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun delete(item: MediaItemEntity) = viewModelScope.launch { mediaDao.delete(item.id) }

    /** Extrait la piste audio en `.m4a` (AAC) à côté du fichier d'origine. */
    fun convertToAudio(item: MediaItemEntity) = viewModelScope.launch {
        val source = localFile(item) ?: return@launch run { _status.value = "Aucun fichier téléchargé pour ce média" }
        _status.value = "Conversion en cours…"
        runCatching {
            exporter.exportToM4a(source, AudioExporter.targetFor(source, source.parentFile!!))
        }.onSuccess { file ->
            _status.value = "Audio exporté : ${file.name}"
        }.onFailure { error ->
            _status.value = error.message ?: "Conversion impossible"
        }
    }

    /** Déplace le fichier dans le coffre chiffré (l'original est supprimé). */
    fun moveToVault(item: MediaItemEntity) = viewModelScope.launch {
        val source = localFile(item) ?: return@launch run { _status.value = "Aucun fichier téléchargé pour ce média" }
        runCatching { vault.moveIntoVault(source) }
            .onSuccess { _status.value = "Fichier chiffré dans le coffre" }
            .onFailure { error -> _status.value = error.message ?: "Mise au coffre impossible" }
    }

    fun clearStatus() {
        _status.value = null
    }

    private suspend fun localFile(item: MediaItemEntity): File? =
        taskDao.completedUriFor(item.id)
            ?.let { uri -> runCatching { File(Uri.parse(uri).path!!) }.getOrNull() }
            ?.takeIf { it.exists() }
}

@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Rechercher dans la bibliothèque") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        status?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall)
        }

        if (items.isEmpty()) {
            Text(
                "Vos médias téléchargés apparaîtront ici.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        item.author?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            DateFormat.getDateInstance().format(Date(item.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { viewModel.convertToAudio(item) }) {
                                Text("Convertir en M4A")
                            }
                            TextButton(onClick = { viewModel.moveToVault(item) }) {
                                Text("Coffrer")
                            }
                        }
                    }
                }
            }
        }
    }
}
