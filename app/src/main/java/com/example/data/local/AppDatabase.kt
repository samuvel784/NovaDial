package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.BackupRecordEntity
import com.example.data.model.BlockedNumberEntity
import com.example.data.model.CallLogEntry
import com.example.data.model.ContactEntity
import com.example.data.model.InterceptedSpamCallEntity
import com.example.data.model.MergedContactMappingEntity
import com.example.data.model.SpamEntryEntity

@Database(
    entities = [
        ContactEntity::class,
        CallLogEntry::class,
        BlockedNumberEntity::class,
        BackupRecordEntity::class,
        SpamEntryEntity::class,
        InterceptedSpamCallEntity::class,
        MergedContactMappingEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun callLogDao(): CallLogDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun backupDao(): BackupDao
    abstract fun spamDao(): SpamDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novadial_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
