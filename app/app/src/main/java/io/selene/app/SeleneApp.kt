package io.selene.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp
import io.selene.app.data.SettingsRepository
import io.selene.app.queue.DownloadSlots
import io.selene.app.resolver.ExternalResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SeleneApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var externalResolver: ExternalResolver

    @Inject
    lateinit var downloadSlots: DownloadSlots

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * WorkManager est initialisé par nous (l'initialiseur par défaut est retiré
     * du manifeste) afin que le worker puisse recevoir ses dépendances Hilt.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        runCatching {
            YoutubeDL.init(this)
            FFmpeg.init(this)
        }.onFailure {
            android.util.Log.e("SeleneApp", "Initialisation yt-dlp impossible", it)
        }
        // Le résolveur externe ne peut pas lire DataStore de façon synchrone : ses réglages lui
        // sont poussés dès qu'ils changent. Tant que ce n'est pas fait, `matches` renvoie faux,
        // donc aucune adresse ne peut partir vers un pont par accident.
        applicationScope.launch {
            settings.settings.collect { current ->
                externalResolver.applySettings(current)
                downloadSlots.setMax(current.downloadSlots)
            }
        }
    }
}
