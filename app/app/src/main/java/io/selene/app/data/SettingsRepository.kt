package io.selene.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "selene.settings",
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val wifiOnly = booleanPreferencesKey("download.wifi_only")
        val clipboardWatch = booleanPreferencesKey("clipboard.watch")
        val theme = stringPreferencesKey("ui.theme")
        val destination = stringPreferencesKey("download.destination_id")
        val externalEnabled = booleanPreferencesKey("external.enabled")
        val externalEndpoint = stringPreferencesKey("external.endpoint")
        val externalToken = stringPreferencesKey("external.token")
        val downloadSlots = intPreferencesKey("download.slots")
        val exportToGallery = booleanPreferencesKey("download.export_to_gallery")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            wifiOnly = prefs[Keys.wifiOnly] ?: false,
            clipboardWatch = prefs[Keys.clipboardWatch] ?: false,
            theme = prefs[Keys.theme]?.let { name ->
                runCatching { ThemeMode.valueOf(name) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            destinationFolderId = prefs[Keys.destination],
            externalResolverEnabled = prefs[Keys.externalEnabled] ?: false,
            externalResolverEndpoint = prefs[Keys.externalEndpoint].orEmpty(),
            externalResolverToken = prefs[Keys.externalToken].orEmpty(),
            downloadSlots = prefs[Keys.downloadSlots] ?: 1,
            exportToGallery = prefs[Keys.exportToGallery] ?: true,
        )
    }

    suspend fun setWifiOnly(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.wifiOnly] = enabled
    }

    suspend fun setClipboardWatch(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.clipboardWatch] = enabled
    }

    suspend fun setExportToGallery(enabled: Boolean) = context.settingsDataStore.edit {
        it[Keys.exportToGallery] = enabled
    }

    suspend fun setDownloadSlots(slots: Int) = context.settingsDataStore.edit {
        it[Keys.downloadSlots] = slots.coerceIn(1, 4)
    }

    suspend fun setTheme(mode: ThemeMode) = context.settingsDataStore.edit {
        it[Keys.theme] = mode.name
    }

    suspend fun setDestinationFolder(folderId: String?) = context.settingsDataStore.edit {
        if (folderId == null) it.remove(Keys.destination) else it[Keys.destination] = folderId
    }

    /**
     * Active le pont externe. Volontairement séparé des autres réglages : une URL et un jeton
     * sont nécessaires, et l'activation seule ne doit pas suffire à envoyer des adresses
     * quelque part. L'écran de réglages vérifie les deux avant d'appeler ceci.
     */
    suspend fun setExternalResolver(enabled: Boolean, endpoint: String, token: String) =
        context.settingsDataStore.edit {
            it[Keys.externalEnabled] = enabled
            it[Keys.externalEndpoint] = endpoint.trim()
            it[Keys.externalToken] = token.trim()
        }
}
