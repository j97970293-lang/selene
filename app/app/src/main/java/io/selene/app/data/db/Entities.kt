package io.selene.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_item",
    indices = [Index(value = ["canonical_url"], unique = true)],
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_id") val sourceId: String?,
    @ColumnInfo(name = "canonical_url") val canonicalUrl: String,
    val title: String,
    val author: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "variant",
    indices = [Index("media_id")],
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VariantEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "media_id") val mediaId: String,
    val kind: String,
    val container: String,
    @ColumnInfo(name = "quality_label") val qualityLabel: String?,
    @ColumnInfo(name = "bitrate_kbps") val bitrateKbps: Int?,
    @ColumnInfo(name = "audio_language") val audioLanguage: String?,
    val width: Int?,
    val height: Int?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    val url: String,
    @ColumnInfo(name = "headers_json") val headersJson: String?,
)

@Entity(
    tableName = "download_task",
    indices = [Index("state"), Index("variant_id")],
    foreignKeys = [
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "variant_id") val variantId: String,
    val state: String,
    @ColumnInfo(name = "bytes_downloaded") val bytesDownloaded: Long,
    @ColumnInfo(name = "bytes_total") val bytesTotal: Long?,
    val attempt: Int,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "destination_uri") val destinationUri: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    /** Plus haut = passé devant. 0 par défaut, 10 pour un téléchargement urgent. */
    val priority: Int = 0,
    /** Ordre choisi par l'utilisateur à l'intérieur d'une même priorité. */
    val position: Long = 0,
)

/** Ligne dénormalisée pour l'écran des téléchargements (une seule requête). */
data class TaskRow(
    val id: String,
    val state: String,
    val title: String,
    val container: String,
    val qualityLabel: String?,
    val bytesDownloaded: Long,
    val bytesTotal: Long?,
    val lastError: String?,
    val destinationUri: String?,
    val priority: Int = 0,
    val position: Long = 0,
) {
    val progress: Float?
        get() = bytesTotal?.takeIf { it > 0 }?.let {
            (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f)
        }
}
