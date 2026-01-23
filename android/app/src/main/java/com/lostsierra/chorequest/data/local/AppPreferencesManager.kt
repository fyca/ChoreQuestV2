package com.lostsierra.chorequest.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "chorequest_app_preferences",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        private const val DEFAULT_SYNC_INTERVAL_MINUTES = 3L
    }

    /**
     * Get sync interval in minutes
     */
    fun getSyncIntervalMinutes(): Long {
        return sharedPreferences.getLong(KEY_SYNC_INTERVAL_MINUTES, DEFAULT_SYNC_INTERVAL_MINUTES)
    }

    /**
     * Set sync interval in minutes
     */
    fun setSyncIntervalMinutes(minutes: Long) {
        // Ensure minimum is 1 minute (WorkManager requirement)
        val validMinutes = minutes.coerceAtLeast(1L)
        sharedPreferences.edit().putLong(KEY_SYNC_INTERVAL_MINUTES, validMinutes).apply()
    }
}
