package com.lostsierra.chorequest.data.drive

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lostsierra.chorequest.data.local.SessionManager
import com.lostsierra.chorequest.data.remote.ChoreQuestApi
import com.lostsierra.chorequest.utils.Constants
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages OAuth access tokens for Drive API calls
 * Handles token storage, retrieval, and refresh
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val api: ChoreQuestApi,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "TokenManager"
        private const val KEY_ACCESS_TOKEN = "drive_access_token"
        private const val KEY_REFRESH_TOKEN = "drive_refresh_token"
        private const val KEY_TOKEN_EXPIRY = "drive_token_expiry"
        private const val PREFS_NAME = "${Constants.StorageKeys.SESSION}_tokens"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        createEncryptedPreferences()
    }

    /**
     * Deletes the corrupted keystore entry and related files
     */
    private fun deleteCorruptedKeystore() {
        try {
            // Delete keystore entry
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            // Try to delete default master key alias (used by MasterKey.Builder)
            try {
                if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                    keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    Log.d(TAG, "Deleted default master key alias: ${MasterKey.DEFAULT_MASTER_KEY_ALIAS}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Default master key alias not found or couldn't be deleted: ${e.message}")
            }
            
            // Try to delete Tink keyset aliases (used by EncryptedSharedPreferences)
            // The alias pattern is: _androidx_security_crypto_encrypted_prefs_key_keyset_<prefs_name>
            val tinkAliasPrefix = "_androidx_security_crypto_encrypted_prefs_key_keyset_"
            val aliases = keyStore.aliases()
            var deletedCount = 0
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement() as String
                // Match aliases that contain our preferences name
                if (alias.startsWith(tinkAliasPrefix) && alias.contains(PREFS_NAME.replace("_", ""))) {
                    try {
                        keyStore.deleteEntry(alias)
                        deletedCount++
                        Log.d(TAG, "Deleted Tink keyset alias: $alias")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not delete alias: $alias", e)
                    }
                }
            }
            
            // Also try to delete any aliases that might be related to our preferences
            // EncryptedSharedPreferences might use variations of the filename
            val aliases2 = keyStore.aliases()
            while (aliases2.hasMoreElements()) {
                val alias = aliases2.nextElement() as String
                // Look for aliases that might be related to our token preferences
                if (alias.contains("token") || alias.contains(PREFS_NAME.replace("_", ""))) {
                    try {
                        // Only delete if it's a Tink-related alias
                        if (alias.startsWith("_androidx_security_crypto") || alias.startsWith("_tink_")) {
                            keyStore.deleteEntry(alias)
                            deletedCount++
                            Log.d(TAG, "Deleted related keystore alias: $alias")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not delete alias: $alias", e)
                    }
                }
            }
            
            Log.d(TAG, "Deleted $deletedCount keystore entries")
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting keystore entries", e)
        }
        
        // Delete corrupted preferences files
        try {
            val prefsFile = File(context.filesDir.parent, "shared_prefs/${PREFS_NAME}.xml")
            if (prefsFile.exists()) {
                prefsFile.delete()
                Log.d(TAG, "Deleted corrupted preferences file: ${prefsFile.absolutePath}")
            }
            
            // Delete any Tink keyset files
            val keysetFile = File(context.filesDir, "keysets/${PREFS_NAME}_master_key")
            if (keysetFile.exists()) {
                keysetFile.delete()
                Log.d(TAG, "Deleted keyset file: ${keysetFile.absolutePath}")
            }
            
            // Also check in shared_prefs for keyset files
            val keysetPrefsFile = File(context.filesDir.parent, "shared_prefs/${PREFS_NAME}_master_key.xml")
            if (keysetPrefsFile.exists()) {
                keysetPrefsFile.delete()
                Log.d(TAG, "Deleted master key preferences file: ${keysetPrefsFile.absolutePath}")
            }
        } catch (deleteException: Exception) {
            Log.w(TAG, "Error deleting corrupted preferences files", deleteException)
        }
    }

    /**
     * Creates EncryptedSharedPreferences with error handling for corrupted files
     * If decryption fails, deletes corrupted keystore and recreates encrypted preferences
     */
    private fun createEncryptedPreferences(): SharedPreferences {
        // Try encrypted preferences with primary master key
        return try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        } catch (e: Exception) {
            // Handle corrupted encrypted preferences
            if (e is AEADBadTagException || 
                e.cause is android.security.KeyStoreException ||
                e.message?.contains("VERIFICATION_FAILED") == true ||
                e.message?.contains("Signature/MAC verification failed") == true) {
                Log.w(TAG, "Encrypted preferences corrupted, deleting keystore and recreating", e)
                
                // Delete corrupted keystore entries and files
                deleteCorruptedKeystore()
                
                // Wait a moment for keystore to update
                Thread.sleep(100)
                
                // Try to recreate encrypted preferences
                try {
                    Log.d(TAG, "Attempting to recreate encrypted preferences with fresh keystore")
                    EncryptedSharedPreferences.create(
                        context,
                        PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    ) as EncryptedSharedPreferences
                } catch (recreateException: Exception) {
                    Log.e(TAG, "Failed to recreate encrypted preferences after cleanup", recreateException)
                    // If recreation fails, delete again and try one more time
                    deleteCorruptedKeystore()
                    Thread.sleep(200)
                    
                    try {
                        EncryptedSharedPreferences.create(
                            context,
                            PREFS_NAME,
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        ) as EncryptedSharedPreferences
                    } catch (finalException: Exception) {
                        Log.e(TAG, "Failed to recreate encrypted preferences after second attempt", finalException)
                        throw RuntimeException("Unable to create encrypted preferences after cleanup. App cannot continue securely.", finalException)
                    }
                }
            } else {
                // Re-throw if it's a different exception
                throw e
            }
        }
    }

    /**
     * Get a valid access token, refreshing if necessary
     * Uses Apps Script to refresh tokens when expired
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            val session = sessionManager.loadSession() ?: run {
                Log.w(TAG, "No session found")
                return@withContext null
            }

            // Check if we have a stored token
            val storedToken = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
            val tokenExpiry = sharedPreferences.getLong(KEY_TOKEN_EXPIRY, 0)
            
            if (storedToken != null && System.currentTimeMillis() < tokenExpiry) {
                Log.d(TAG, "Using stored access token")
                return@withContext storedToken
            }
            
            // Token expired or not found - refresh via Apps Script
            Log.d(TAG, "Token expired or not found, refreshing via Apps Script")
            Log.d(TAG, "Using ownerEmail from session (originally from QR code): ${session.ownerEmail}")
            
            try {
                // ownerEmail comes from session, which was set during QR code authentication
                val response = api.refreshAccessToken(
                    userId = session.userId,
                    token = session.authToken,
                    ownerEmail = session.ownerEmail // From QR code, stored in session
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val newAccessToken = response.body()?.accessToken
                    val expiresIn = response.body()?.expiresIn ?: 3600
                    
                    if (newAccessToken != null) {
                        // Store the new token
                        storeAccessToken(newAccessToken, expiresIn.toLong())
                        Log.d(TAG, "Token refreshed successfully via Apps Script")
                        return@withContext newAccessToken
                    } else {
                        Log.w(TAG, "Token refresh succeeded but no access token in response")
                    }
                } else {
                    val errorMsg = response.body()?.error ?: response.message()
                    Log.w(TAG, "Token refresh failed: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing token via Apps Script", e)
            }
            
            // If refresh failed, return null to fall back to Apps Script
            Log.d(TAG, "No valid token found, will use Apps Script for now")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token", e)
            null
        }
    }

    /**
     * Store access token securely
     */
    fun storeAccessToken(accessToken: String, expiresInSeconds: Long = 3600) {
        try {
            val expiryTime = System.currentTimeMillis() + (expiresInSeconds * 1000)
            sharedPreferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_TOKEN_EXPIRY, expiryTime)
                .apply()
            Log.d(TAG, "Access token stored, expires in ${expiresInSeconds}s")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing access token", e)
        }
    }

    /**
     * Store refresh token securely
     */
    fun storeRefreshToken(refreshToken: String) {
        try {
            sharedPreferences.edit()
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply()
            Log.d(TAG, "Refresh token stored")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing refresh token", e)
        }
    }

    /**
     * Force refresh the access token by clearing stored token and refreshing via Apps Script
     * Use this when a 401 error is encountered
     */
    suspend fun forceRefreshToken(): String? = withContext(Dispatchers.IO) {
        try {
            val session = sessionManager.loadSession() ?: run {
                Log.w(TAG, "No session found for token refresh")
                return@withContext null
            }

            // Clear the stored token to force refresh
            sharedPreferences.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_TOKEN_EXPIRY)
                .apply()
            Log.d(TAG, "Cleared stored token, refreshing via Apps Script")

            // Refresh via Apps Script
            Log.d(TAG, "Force refreshing token using ownerEmail from session: ${session.ownerEmail}")
            try {
                // ownerEmail comes from session, which was set during QR code authentication
                val response = api.refreshAccessToken(
                    userId = session.userId,
                    token = session.authToken,
                    ownerEmail = session.ownerEmail // From QR code, stored in session
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val newAccessToken = response.body()?.accessToken
                    val expiresIn = response.body()?.expiresIn ?: 3600
                    
                    if (newAccessToken != null) {
                        // Store the new token
                        storeAccessToken(newAccessToken, expiresIn.toLong())
                        Log.d(TAG, "Token force-refreshed successfully via Apps Script")
                        return@withContext newAccessToken
                    } else {
                        Log.w(TAG, "Token refresh succeeded but no access token in response")
                    }
                } else {
                    val errorMsg = response.body()?.error ?: response.message()
                    Log.w(TAG, "Token refresh failed: $errorMsg")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error force-refreshing token via Apps Script", e)
            }
            
            Log.d(TAG, "Token force-refresh failed")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error in forceRefreshToken", e)
            null
        }
    }

    /**
     * Clear stored tokens
     */
    fun clearTokens() {
        try {
            sharedPreferences.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRY)
                .apply()
            Log.d(TAG, "Tokens cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing tokens", e)
        }
    }
}
