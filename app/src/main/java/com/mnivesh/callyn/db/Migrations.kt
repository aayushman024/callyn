package com.mnivesh.callyn.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE contacts ADD COLUMN dob TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `personal_call_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `duration` INTEGER NOT NULL, 
                    `timestamp` INTEGER NOT NULL, 
                    `direction` TEXT NOT NULL, 
                    `simSlot` TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }
}