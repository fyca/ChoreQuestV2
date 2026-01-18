package com.chorequest.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 1 to 2
 * Adds reward_redemptions table
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create reward_redemptions table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS reward_redemptions (
                id TEXT NOT NULL PRIMARY KEY,
                userId TEXT NOT NULL,
                userName TEXT,
                rewardId TEXT NOT NULL,
                rewardTitle TEXT,
                status TEXT NOT NULL,
                requestedAt TEXT NOT NULL,
                approvedBy TEXT,
                approvedAt TEXT,
                deniedBy TEXT,
                deniedAt TEXT,
                denialReason TEXT,
                completedAt TEXT,
                pointCost INTEGER NOT NULL
            )
        """.trimIndent())
    }
}
