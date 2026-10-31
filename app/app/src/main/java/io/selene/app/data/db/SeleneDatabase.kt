package io.selene.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaItemEntity::class, VariantEntity::class, DownloadTaskEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SeleneDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun variantDao(): VariantDao
    abstract fun taskDao(): TaskDao

    companion object {
        /**
         * v2 : file priorisée. Deux colonnes ajoutées, aucune donnée touchée — les
         * téléchargements en cours survivent à la mise à jour.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_task ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_task ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE download_task SET position = (SELECT COUNT(*) FROM download_task t2 " +
                        "WHERE t2.created_at < download_task.created_at)",
                )
            }
        }
    }
}
