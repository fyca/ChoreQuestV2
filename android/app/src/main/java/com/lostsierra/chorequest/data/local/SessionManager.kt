package com.lostsierra.chorequest.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lostsierra.chorequest.domain.models.DeviceSession
import com.lostsierra.chorequest.utils.Constants
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
        android.util.Log.d("SessionManager", "saveSession() - saving session: userId=${session.userId}, familyId=${session.familyId}, json length=${json.length}")
        sharedPreferences.edit().putString(KEY_SESSION, json).apply()
        android.util.Log.d("SessionManager", "saveSession() - session saved, verifying...")
        // Verify it was saved
        val saved = sharedPreferences.getString(KEY_SESSION, null)
        android.util.Log.d("SessionManager", "saveSession() - verification: ${if (saved != null) "saved successfully (${saved.length} chars)" else "FAILED - not found"}")
    }

    /**
     * Load session
     */
    fun loadSession(): DeviceSession? {
        val json = sharedPreferences.getString(KEY_SESSION, null)
        android.util.Log.d("SessionManager", "loadSession() - json is ${if (json != null) "present (${json.length} chars)" else "null"}")
        if (json == null) {
            return null
        }
        return try {
            val session = gson.fromJson(json, DeviceSession::class.java)
            android.util.Log.d("SessionManager", "loadSession() - loaded session: userId=${session.userId}, familyId=${session.familyId}")
            session
        } catch (e: Exception) {
            android.util.Log.e("SessionManager", "Error loading session", e)
            null
        }
    }

    /**
     * Clear session
     */
    fun clearSession() {
        android.util.Log.d("SessionManager", "clearSession() called")
        sharedPreferences.edit().clear().apply()
    }

    /**
     * Check if session exists
     */
    fun hasValidSession(): Boolean {
        val hasSession = loadSession() != null
        android.util.Log.d("SessionManager", "hasValidSession() = $hasSession")
        return hasSession
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
