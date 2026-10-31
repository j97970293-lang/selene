package io.selene.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.selene.app.queue.QueueRow
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Upsert
    suspend fun upsert(item: MediaItemEntity)

    @Query("SELECT * FROM media_item WHERE id = :id")
    suspend fun byId(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_item WHERE canonical_url = :url LIMIT 1")
    suspend fun byCanonicalUrl(url: String): MediaItemEntity?

    @Query(
        """
        SELECT * FROM media_item
        WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'
        ORDER BY created_at DESC
        """,
    )
    fun observeSearch(query: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_item ORDER BY created_at DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("DELETE FROM media_item WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VariantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(variants: List<VariantEntity>)

    @Query("SELECT * FROM variant WHERE id = :id")
    suspend fun byId(id: String): VariantEntity?

    @Query("SELECT * FROM variant WHERE media_id = :mediaId")
    suspend fun byMedia(mediaId: String): List<VariantEntity>
}

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_task WHERE id = :id")
    suspend fun byId(id: String): DownloadTaskEntity?

    @Query(
        """
        SELECT t.id AS id, t.state AS state, m.title AS title, v.container AS container,
               v.quality_label AS qualityLabel, t.bytes_downloaded AS bytesDownloaded,
               t.bytes_total AS bytesTotal, t.last_error AS lastError,
               t.destination_uri AS destinationUri,
               t.priority AS priority, t.position AS position
        FROM download_task t
        JOIN variant v ON v.id = t.variant_id
        JOIN media_item m ON m.id = v.media_id
        ORDER BY t.created_at DESC
        """,
    )
    fun observeAll(): Flow<List<TaskRow>>

    @Query(
        """
        SELECT t.id AS id, t.state AS state, m.title AS title, v.container AS container,
               v.quality_label AS qualityLabel, t.bytes_downloaded AS bytesDownloaded,
               t.bytes_total AS bytesTotal, t.last_error AS lastError,
               t.destination_uri AS destinationUri,
               t.priority AS priority, t.position AS position
        FROM download_task t
        JOIN variant v ON v.id = t.variant_id
        JOIN media_item m ON m.id = v.media_id
        WHERE t.state IN ('QUEUED', 'RUNNING', 'PAUSED')
        ORDER BY t.priority DESC, t.position ASC, t.created_at ASC
        """,
    )
    fun observeActive(): Flow<List<TaskRow>>

    @Query("UPDATE download_task SET state = :state, updated_at = :now WHERE id = :id")
    suspend fun setState(id: String, state: String, now: Long)

    @Query(
        """
        UPDATE download_task
        SET bytes_downloaded = :downloaded, bytes_total = :total, updated_at = :now
        WHERE id = :id
        """,
    )
    suspend fun setProgress(id: String, downloaded: Long, total: Long?, now: Long)

    @Query(
        """
        UPDATE download_task
        SET state = 'DONE', destination_uri = :destination, bytes_downloaded = :downloaded,
            completed_at = :now, updated_at = :now, last_error = NULL
        WHERE id = :id
        """,
    )
    suspend fun setCompleted(id: String, destination: String, downloaded: Long, now: Long)

    @Query(
        """
        UPDATE download_task
        SET state = 'FAILED', last_error = :error, attempt = attempt + 1, updated_at = :now
        WHERE id = :id
        """,
    )
    suspend fun setFailed(id: String, error: String, now: Long)

    @Query("UPDATE download_task SET priority = :priority, updated_at = :now WHERE id = :id")
    suspend fun setPriority(id: String, priority: Int, now: Long)

    @Query("UPDATE download_task SET position = :position, updated_at = :now WHERE id = :id")
    suspend fun setPosition(id: String, position: Long, now: Long)

    @Query("SELECT COALESCE(MAX(position), 0) FROM download_task WHERE state IN ('QUEUED', 'PAUSED')")
    suspend fun maxPosition(): Long

    @Query(
        "SELECT id, priority, position FROM download_task WHERE state IN ('QUEUED', 'PAUSED') " +
            "ORDER BY priority DESC, position ASC",
    )
    suspend fun activeEntries(): List<QueueRow>

    /** Chemin local du dernier téléchargement terminé pour un média donné. */
    @Query(
        """
        SELECT t.destination_uri FROM download_task t
        JOIN variant v ON v.id = t.variant_id
        WHERE v.media_id = :mediaId AND t.state = 'DONE' AND t.destination_uri IS NOT NULL
        ORDER BY t.completed_at DESC LIMIT 1
        """,
    )
    suspend fun completedUriFor(mediaId: String): String?

    @Query("DELETE FROM download_task WHERE id = :id")
    suspend fun delete(id: String)
}
