package com.taskmanager.android.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedListEntity::class,
        CachedTaskEntity::class,
        PendingSyncOperationEntity::class,
        SyncStateEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class TaskManagerDatabase : RoomDatabase() {
    abstract fun taskManagerDao(): TaskManagerDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN reminder_time TEXT")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN deleted_at TEXT")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN repeat_config_json TEXT")
            }
        }
    }
}
