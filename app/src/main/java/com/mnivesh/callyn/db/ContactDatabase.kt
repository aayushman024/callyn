package com.mnivesh.callyn.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [AppContact::class, WorkCallLog::class, CrmContact::class, PersonalCallLog::class], version = 17, exportSchema = false)
abstract class ContactDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun workCallLogDao(): WorkCallLogDao
    abstract fun personalCallLogDao(): PersonalCallLogDao

    companion object {
        @Volatile
        private var INSTANCE: ContactDatabase? = null


        // Update method to accept the passphrase
        fun getDatabase(context: Context, passphrase: ByteArray): ContactDatabase {
            return INSTANCE ?: synchronized(this) {
                // Ensure SQLCipher native library is loaded
                System.loadLibrary("sqlcipher")

                // 1. Initialize the support factory with the passphrase
                val factory = SupportOpenHelperFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContactDatabase::class.java,
                    "contact_database"
                )
                    .openHelperFactory(factory)
                    .addMigrations(
                        DatabaseMigrations.MIGRATION_14_15,
                        DatabaseMigrations.MIGRATION_15_16,
                        DatabaseMigrations.MIGRATION_16_17
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}