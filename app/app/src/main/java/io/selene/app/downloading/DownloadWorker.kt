package io.selene.app.downloading

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.selene.app.data.db.SeleneDatabase
import io.selene.app.model.TaskState
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Exécute un téléchargement, avec progression persistée (throttlée) et
 * notification de premier plan.
 *
 * Décision v1 : le fichier est écrit dans `filesDir/media/` (aucune permission
 * de stockage requise) et exposé via FileProvider. L'export vers
 * `Downloads/` par MediaStore est une étape suivante documentée.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: DownloadEngine,
    private val hlsDownloader: HlsDownloader,
    private val dashDownloader: DashDownloader,
    private val database: SeleneDatabase,
    private val notifier: DownloadNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val taskDao = database.taskDao()

        val task = taskDao.byId(taskId) ?: return Result.failure()
        val variant = database.variantDao().byId(task.variantId) ?: return Result.failure()
        val media = database.mediaDao().byId(variant.mediaId) ?: return Result.failure()

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

            val file = when {
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

            taskDao.setCompleted(taskId, Uri.fromFile(file).toString(), file.length(), System.currentTimeMillis())
            notifier.notifyCompleted(taskId, media.title)
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

    companion object {
        const val KEY_TASK_ID = "io.selene.app.extra.TASK_ID"
        private const val PROGRESS_INTERVAL_MS = 250L
    }
}
