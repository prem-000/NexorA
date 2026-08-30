package com.fury.peerconnect.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PeerEntity::class, MessageEntity::class, AlertEntity::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun peerDao(): PeerDao
    abstract fun messageDao(): MessageDao
    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN alertId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE alerts ADD COLUMN attachmentPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN alertId TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "peer_connect_db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}