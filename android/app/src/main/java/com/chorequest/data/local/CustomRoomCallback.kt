package com.chorequest.data.local

import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Custom Room callback to handle schema mismatches
 * This will clear the database if there's a schema integrity issue
 */
class CustomRoomCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        Log.d("CustomRoomCallback", "Database opened successfully")
    }
    
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        Log.d("CustomRoomCallback", "Database created")
    }
    
    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        super.onDestructiveMigration(db)
        Log.d("CustomRoomCallback", "Destructive migration applied - database cleared")
    }
}
