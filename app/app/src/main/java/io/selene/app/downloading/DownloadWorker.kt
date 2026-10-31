package io.selene.app.downloading

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.selene.app.data.SettingsRepository
import io.selene.app.data.db.SeleneDatabase
import io.selene.app.data.db.TaskDao
import io.selene.app.data.db.toVariant
import io.selene.app.model.TaskState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Exécute un téléchargement, avec progression persistée (throttlée) et
 * notification de premier plan.
 *
 * Le fichier est écrit dans `filesDir/media/`, lisible par l'application seule, puis
 * copié vers le stockage partagé (`Movies/`, `Music/`, `Download/`) par
 * [MediaExporter] si l'export est activé. La copie est facultative : son échec ne
 * fait pas échouer le téléchargement.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: DownloadEngine,
    private val hlsDownloader: HlsDownloader,
    private val dashDownloader: DashDownloader,
    private val ytDlpDownloader: YtDlpDownloader,
    private val database: SeleneDatabase,
    private val notifier: DownloadNotifier,
    private val slots: io.selene.app.queue.DownloadSlots,
    private val exporter: MediaExporter,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val taskDao = database.taskDao()

        val task = taskDao.byId(taskId) ?: return Result.failure()
        val variant = database.variantDao().byId(task.variantId) ?: return Result.failure()
        val media = database.mediaDao().byId(variant.mediaId) ?: return Result.failure()
        // L'entité Room n'est pas l'objet métier : le moteur attend un `Variant`.
        val model = variant.toVariant()

        if (task.state == TaskState.DONE.name || task.state == TaskState.CANCELLED.name) {
            return Result.success()
        }

        notifier.ensureChannels()
        val now = System.currentTimeMillis()
        taskDao.setState(taskId, TaskState.RUNNING.name, now)
        setForeground(notifier.foregroundInfo(taskId, media.title, task.bytesDownloaded, task.bytesTotal))

        val directory = File(applicationContext.filesDir, "media").apply { mkdirs() }
        val target = FileNamer.uniqueFile(
            directory,
            FileNamer.sanitize(media.title, variant.container),
        )

        // Un flux se télécharge segment par segment, pas comme un fichier unique.
        val isDash = variant.id.startsWith("dash:")
        val isHls = !isDash && (
            variant.container.equals("ts", ignoreCase = true) ||
                variant.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
            )

        return try {
            var lastPersist = 0L
            // Respecte le réglage « téléchargements simultanés » (1 à 4).
            val onProgress: suspend (Long, Long?) -> Unit = { downloaded, total ->
                val tick = System.currentTimeMillis()
                if (tick - lastPersist >= PROGRESS_INTERVAL_MS) {
                    lastPersist = tick
                    taskDao.setProgress(taskId, downloaded, total, tick)
                    setForeground(
                        notifier.foregroundInfo(taskId, media.title, downloaded, total),
                    )
                }
            }

            val file = slots.withSlot {
                when {
                    variant.id.startsWith("yt-dlp:") -> ytDlpDownloader.download(
                        pageUrl = media.canonicalUrl,
                        variant = model,
                        target = target,
                        onProgress = onProgress,
                    )

                    isDash -> dashDownloader.download(
                        manifestUrl = variant.url,
                        representationId = variant.id.removePrefix("dash:"),
                        target = target,
                        onProgress = onProgress,
                    )

                    isHls -> hlsDownloader.download(
                        playlistUrl = variant.url,
                        target = target,
                        onProgress = onProgress,
                    )

                    else -> engine.download(variant.url, target, onProgress = onProgress)
                }
            }

            taskDao.setCompleted(taskId, Uri.fromFile(file).toString(), file.length(), System.currentTimeMillis())
            notifier.notifyCompleted(taskId, media.title)
            publishToGallery(taskId, taskDao, file)
            Result.success()
        } catch (cancelled: CancellationException) {
            taskDao.setState(taskId, TaskState.PAUSED.name, System.currentTimeMillis())
            throw cancelled
        } catch (error: Exception) {
            val message = error.message ?: error::class.simpleName ?: "erreur inconnue"
            taskDao.setFailed(taskId, message, System.currentTimeMillis())
            notifier.cancel(taskId)
            Result.failure()
        }
    }

    /**
     * Copie le fichier dans le stockage partagé pour qu'il apparaisse dans la
     * galerie. Un échec ici ne remet pas en cause le téléchargement : le fichier
     * reste lisible dans Sélène, l'utilisateur peut relancer l'export depuis la
     * bibliothèque.
     */
    private suspend fun publishToGallery(taskId: String, taskDao: TaskDao, file: File) {
        if (!settings.settings.first().exportToGallery) return
        when (val result = exporter.export(file)) {
            is ExportResult.Saved ->
                taskDao.setExported(taskId, result.uri.toString(), System.currentTimeMillis())

            ExportResult.PermissionRequired -> android.util.Log.i(
                "DownloadWorker",
                "Export ignoré : permission de stockage non accordée",
            )

            is ExportResult.Failed -> android.util.Log.w(
                "DownloadWorker",
                "Export vers la galerie impossible : ${result.message}",
            )
        }
    }

    companion object {
        const val KEY_TASK_ID = "io.selene.app.extra.TASK_ID"
        private const val PROGRESS_INTERVAL_MS = 250L
    }
}
