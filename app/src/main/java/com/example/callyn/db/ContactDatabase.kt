package com.example.callyn.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [AppContact::class, WorkCallLog::class], version = 5, exportSchema = false)
abstract class ContactDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun workCallLogDao(): WorkCallLogDao

    companion object {
        @Volatile
        private var INSTANCE: ContactDatabase? = null

        // Update method to accept the passphrase
        fun getDatabase(context: Context, passphrase: ByteArray): ContactDatabase {
            return INSTANCE ?: synchronized(this) {
                // 1. Initialize the support factory with the passphrase
                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContactDatabase::class.java,
                    "contact_database"
                )
                    .openHelperFactory(factory) // 2. Apply the factory here
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}