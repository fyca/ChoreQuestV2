package com.lostsierra.chorequest.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 3 to 4
 * Adds requirePhotoProof column to chores table
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add requirePhotoProof column to chores table
        database.execSQL("""
            ALTER TABLE chores ADD COLUMN requirePhotoProof INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
    }
}
