package com.chorequest.data.drive

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chorequest.data.local.SessionManager
import com.chorequest.data.remote.ChoreQuestApi
import com.chorequest.utils.Constants
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: EncryptedSharedPreferences = EncryptedSharedPreferences.create(
        context,
        "${Constants.StorageKeys.SESSION}_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    ) as EncryptedSharedPreferences

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
            
            try {
                val response = api.refreshAccessToken(
                    userId = session.userId,
                    token = session.authToken
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
