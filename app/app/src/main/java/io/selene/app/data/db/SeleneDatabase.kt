package io.selene.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MediaItemEntity::class, VariantEntity::class, DownloadTaskEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SeleneDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun variantDao(): VariantDao
    abstract fun taskDao(): TaskDao
}
