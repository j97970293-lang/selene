package io.selene.app.downloading

import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.selene.app.data.db.DownloadTaskEntity
import io.selene.app.data.db.MediaItemEntity
import io.selene.app.data.db.SeleneDatabase
import io.selene.app.data.db.VariantEntity
import io.selene.app.model.ResolvedMedia
import io.selene.app.model.TaskState
import io.selene.app.model.Variant
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val database: SeleneDatabase,
    private val workManager: WorkManager,
) {

    fun observeTasks(): Flow<List<io.selene.app.data.db.TaskRow>> =
        database.taskDao().observeAll()

    /**
     * Persiste le média, ses variantes, puis met la variante choisie en file.
     * Le média est dédupliqué sur son URL canonique.
     */
    suspend fun enqueue(
        media: ResolvedMedia,
        variant: Variant,
        wifiOnly: Boolean,
    ): String {
        val now = System.currentTimeMillis()

        val existing = database.mediaDao().byCanonicalUrl(media.canonicalUrl)
        val mediaId = existing?.id ?: UUID.randomUUID().toString()

        database.mediaDao().upsert(
            MediaItemEntity(
                id = mediaId,
                sourceId = media.sourceId,
                canonicalUrl = media.canonicalUrl,
                title = media.title,
                author = media.author,
                durationMs = media.durationMs,
                thumbnailPath = null,
                createdAt = existing?.createdAt ?: now,
            ),
        )

        // On persiste toutes les variantes connues : l'utilisateur pourra en
        // télécharger une autre plus tard sans re-résoudre l'URL.
        database.variantDao().insertAll(
            media.variants.map { it.toEntity(mediaId) },
        )

        val taskId = UUID.randomUUID().toString()
        database.taskDao().insert(
            DownloadTaskEntity(
                id = taskId,
                variantId = variant.id,
                state = TaskState.QUEUED.name,
                bytesDownloaded = 0,
                bytesTotal = variant.sizeBytes,
                attempt = 0,
                lastError = null,
                destinationUri = null,
                createdAt = now,
                updatedAt = now,
                completedAt = null,
            ),
        )

        schedule(taskId, wifiOnly)
        return taskId
    }

    suspend fun pause(taskId: String) {
        workManager.cancelUniqueWork(workName(taskId))
        database.taskDao().setState(taskId, TaskState.PAUSED.name, System.currentTimeMillis())
    }

    suspend fun resume(taskId: String, wifiOnly: Boolean) {
        database.taskDao().setState(taskId, TaskState.QUEUED.name, System.currentTimeMillis())
        schedule(taskId, wifiOnly)
    }

    suspend fun cancel(taskId: String) {
        workManager.cancelUniqueWork(workName(taskId))
        val task = database.taskDao().byId(taskId) ?: return
        // Le fichier partiel est supprimé par le worker à l'annulation.
        database.taskDao().setState(taskId, TaskState.CANCELLED.name, System.currentTimeMillis())
        task.destinationUri?.let { uri ->
            runCatching { java.io.File(Uri.parse(uri).path ?: return@let).delete() }
        }
    }

    suspend fun deleteRecord(taskId: String) {
        workManager.cancelUniqueWork(workName(taskId))
        database.taskDao().delete(taskId)
    }

    private fun schedule(taskId: String, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(DownloadWorker.KEY_TASK_ID to taskId))
            .addTag(TAG_DOWNLOADS)
            .build()

        workManager.enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.KEEP, request)
    }

    private fun workName(taskId: String) = "download-$taskId"

    companion object {
        const val TAG_DOWNLOADS = "selene.downloads"
    }
}

private fun Variant.toEntity(mediaId: String) = VariantEntity(
    id = id,
    mediaId = mediaId,
    kind = kind.name,
    container = container,
    qualityLabel = qualityLabel,
    bitrateKbps = bitrateKbps,
    audioLanguage = audioLanguage,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    url = url,
    headersJson = null,
)
