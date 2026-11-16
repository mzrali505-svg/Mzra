package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProxyEntity::class,
        BrowserProfileEntity::class,
        BrowserHistoryEntity::class,
        BookmarkEntity::class,
        AutomationFlowEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class CloakDeckDatabase : RoomDatabase() {
    abstract fun proxyDao(): ProxyDao
    abstract fun profileDao(): ProfileDao
    abstract fun historyDao(): HistoryDao
    abstract fun automationDao(): AutomationDao

    companion object {
        @Volatile
        private var instance: CloakDeckDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN humanizeScrollStyle TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE proxies ADD COLUMN humanizeScrollStyle TEXT NOT NULL DEFAULT 'AUTO'")
            }
        }

        fun getInstance(context: Context): CloakDeckDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CloakDeckDatabase::class.java,
                    "cloakdeck_vault.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
