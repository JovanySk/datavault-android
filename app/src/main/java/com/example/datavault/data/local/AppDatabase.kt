package com.example.datavault.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.datavault.data.local.dao.FileActionDao
import com.example.datavault.data.local.dao.FileBookmarkDao
import com.example.datavault.data.local.dao.VaultFileDao
import com.example.datavault.data.local.entity.VaultFile
import com.example.datavault.data.local.entity.FileAction
import com.example.datavault.data.local.entity.FileBookmark

@Database(
    entities = [FileBookmark::class, FileAction::class, VaultFile::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun fileBookmarkDao(): FileBookmarkDao
    abstract fun fileActionDao(): FileActionDao
    abstract fun vaultFileDao(): VaultFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "datavault_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
