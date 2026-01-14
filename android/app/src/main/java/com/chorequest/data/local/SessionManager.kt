package com.chorequest.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chorequest.domain.models.DeviceSession
import com.chorequest.utils.Constants
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        Constants.StorageKeys.SESSION,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Save session
     */
    fun saveSession(session: DeviceSession) {
        val json = gson.toJson(session)
        sharedPreferences.edit().putString(KEY_SESSION, json).apply()
    }

    /**
     * Load session
     */
    fun loadSession(): DeviceSession? {
        val json = sharedPreferences.getString(KEY_SESSION, null) ?: return null
        return try {
            gson.fromJson(json, DeviceSession::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clear session
     */
    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }

    /**
     * Check if session exists
     */
    fun hasValidSession(): Boolean {
        return loadSession() != null
    }

    /**
     * Update session
     */
    fun updateSession(updates: (DeviceSession) -> DeviceSession) {
        val session = loadSession() ?: return
        val updated = updates(session)
        saveSession(updated)
    }

    /**
     * Get current user ID
     */
    fun getCurrentUserId(): String? {
        return loadSession()?.userId
    }

    /**
     * Get current family ID
     */
    fun getCurrentFamilyId(): String? {
        return loadSession()?.familyId
    }

    companion object {
        private const val KEY_SESSION = "device_session"
    }
}
