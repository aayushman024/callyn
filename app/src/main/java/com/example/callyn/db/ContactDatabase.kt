package com.example.callyn.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// UPDATE: Version bumped to 4 to handle the new 'pan' and 'familyHead' columns
@Database(entities = [AppContact::class, WorkCallLog::class], version = 5, exportSchema = false)
abstract class ContactDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun workCallLogDao(): WorkCallLogDao

    companion object {
        @Volatile
        private var INSTANCE: ContactDatabase? = null

        fun getDatabase(context: Context): ContactDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContactDatabase::class.java,
                    "contact_database"
                )
                    .fallbackToDestructiveMigration() // This handles the schema change by recreating the db
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}