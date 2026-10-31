package io.selene.app.downloading

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Démarrage du moteur yt-dlp, une seule fois, et sans faire tomber l'application.
 *
 * L'initialisation extrait un interpréteur Python et un binaire FFmpeg de
 * plusieurs dizaines de mégaoctets. Elle peut échouer pour de bonnes raisons
 * (espace insuffisant, architecture inattendue). Jusqu'ici elle était tentée au
 * démarrage, l'erreur était avalée, et l'utilisateur ne voyait qu'un « yt-dlp a
 * échoué » sans savoir pourquoi. Ici l'échec est retenu et restitué.
 *
 * Le [Mutex] évite que deux téléchargements simultanés lancent deux
 * initialisations en parallèle sur le même dossier.
 */
@Singleton
class YtDlpEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val mutex = Mutex()

    @Volatile
    private var ready = false

    @Volatile
    private var lastError: String? = null

    val isReady: Boolean get() = ready

    /** Initialise si nécessaire ; idempotent et sûr depuis plusieurs coroutines. */
    suspend fun ensureReady(): Result<Unit> = mutex.withLock {
        if (ready) return@withLock Result.success(Unit)
        withContext(Dispatchers.IO) {
            runCatching {
                YoutubeDL.init(context)
                FFmpeg.init(context)
            }.onSuccess {
                ready = true
                lastError = null
            }.onFailure { error ->
                lastError = error.message ?: error::class.simpleName ?: "initialisation impossible"
            }
        }
    }

    /** Version lisible du binaire, ou `null` si le moteur ne répond pas. */
    suspend fun version(): String? = withContext(Dispatchers.IO) {
        if (ensureReady().isFailure) return@withContext null
        runCatching { YoutubeDL.getInstance().version(context) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** Dernière erreur d'initialisation, pour l'écran de réglages. */
    fun errorOrNull(): String? = lastError

    /**
     * Met à jour le binaire yt-dlp. Utile quand un site change et que la version
     * embarquée ne le lit plus : l'utilisateur peut se dépanner sans attendre une
     * nouvelle version de l'application.
     */
    suspend fun update(): Result<String> = withContext(Dispatchers.IO) {
        val startup = ensureReady()
        val failure = startup.exceptionOrNull()
        if (failure != null) {
            return@withContext Result.failure<String>(failure)
        }
        runCatching {
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            // `UpdateStatus` vient d'une bibliothèque compilée en Kotlin : le compilateur
            // le voit comme type de plateforme, d'où la branche `else` obligatoire.
            when (status) {
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Déjà à jour"
                YoutubeDL.UpdateStatus.DONE -> "Mise à jour installée"
                else -> "Mise à jour terminée"
            }
        }
    }
}
