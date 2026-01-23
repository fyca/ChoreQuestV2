package com.lostsierra.chorequest.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.lostsierra.chorequest.data.remote.AuthResponse
import retrofit2.Response

/**
 * Helper class to handle Drive authorization errors
 */
object AuthorizationHelper {
    private const val TAG = "AuthorizationHelper"
    
    /**
     * Check if an error message indicates authorization is needed
     */
    fun isAuthorizationError(errorMessage: String): Boolean {
        val lowerMessage = errorMessage.lowercase()
        val isAuth = lowerMessage.contains("authorization_required") ||
               lowerMessage.contains("drive access not authorized") ||
               lowerMessage.contains("unauthorized") ||
               lowerMessage.contains("401") ||
               lowerMessage.contains("oauth required") ||
               lowerMessage.contains("oauth authorization") ||
               lowerMessage.contains("oauth session mismatch") ||
               lowerMessage.contains("folder owner mismatch") ||
               lowerMessage.contains("folder belongs to")
        Log.d(TAG, "Checking authorization error: '$errorMessage' -> $isAuth")
        return isAuth
    }
    
    /**
     * Extract authorization URL from error message
     * Format: "AUTHORIZATION_REQUIRED:URL:MESSAGE"
     */
    fun extractAuthorizationUrl(errorMessage: String): String? {
        if (errorMessage.startsWith("AUTHORIZATION_REQUIRED:")) {
            val parts = errorMessage.split(":", limit = 3)
            if (parts.size >= 2) {
                return parts[1]
            }
        }
        
        // Check for "Please visit:" or "Please visit the web app URL:" patterns
        val visitPattern = Regex("(?:Please visit|visit)[^:]*:?\\s*(https?://[^\\s\\.]+)", RegexOption.IGNORE_CASE)
        val visitMatch = visitPattern.find(errorMessage)
        if (visitMatch != null) {
            return visitMatch.groupValues[1]
        }
        
        // Try to extract URL from error message
        val urlPattern = Regex("https?://[^\\s]+")
        return urlPattern.find(errorMessage)?.value
    }
    
    /**
     * Extract user-friendly message from error
     */
    fun extractErrorMessage(errorMessage: String): String {
        if (errorMessage.startsWith("AUTHORIZATION_REQUIRED:")) {
            val parts = errorMessage.split(":", limit = 3)
            if (parts.size >= 3) {
                return parts[2]
            }
        }
        
        // Check for OAuth required errors
        if (errorMessage.contains("OAuth required", ignoreCase = true) ||
            errorMessage.contains("Folder owner mismatch", ignoreCase = true)) {
            return "OAuth authorization required. The app needs permission to access your Google Drive. Please authorize the app in your browser."
        }
        
        return "Drive access not authorized. Please authorize the app to access your Google Drive."
    }
    
    /**
     * Get the base web app URL for authorization
     * This is the Apps Script deployment URL
     */
    fun getBaseAuthorizationUrl(): String {
        // Get from Constants - this should be the Apps Script web app URL
        // The URL already includes /exec, so we just return it as-is
        val baseUrl = Constants.APPS_SCRIPT_WEB_APP_URL
        Log.d(TAG, "Base authorization URL: $baseUrl")
        return baseUrl
    }
    
    /**
     * Open authorization URL in browser
     */
    fun openAuthorizationUrl(context: Context, url: String? = null) {
        try {
            // Use the provided URL, or fall back to the base URL from Constants
            val rawUrl = url?.takeIf { it.isNotBlank() } ?: getBaseAuthorizationUrl()
            Log.d(TAG, "Raw URL: $rawUrl")
            
            // Ensure URL has proper protocol (https://)
            val finalUrl = when {
                rawUrl.startsWith("https://") -> rawUrl
                rawUrl.startsWith("http://") -> rawUrl
                rawUrl.startsWith("//") -> "https:$rawUrl"  // Fix //script to https://script
                rawUrl.startsWith("/") -> "https://$rawUrl"  // Fix /script to https://script
                else -> "https://$rawUrl"
            }
            
            Log.d(TAG, "Final URL: $finalUrl")
            
            // Validate URL
            val uri = try {
                Uri.parse(finalUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse URL: $finalUrl", e)
                return
            }
            
            Log.d(TAG, "Parsed URI: $uri")
            
            val intent = Intent(Intent.ACTION_VIEW, uri)
            
            // Check if there's an activity that can handle this intent
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(intent, 0)
            
            Log.d(TAG, "Query result: ${activities.size} activities found")
            
            if (activities.isEmpty()) {
                Log.e(TAG, "No activity found to handle URL: $finalUrl")
                // Try without chooser as fallback
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.d(TAG, "Launched intent directly (fallback)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch intent directly: ${e.message}", e)
                }
                return
            }
            
            // Use chooser to let user pick browser
            val chooser = Intent.createChooser(intent, "Open in browser")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // Always add NEW_TASK flag for consistency
            try {
                context.startActivity(chooser)
                Log.d(TAG, "Successfully launched browser chooser")
            } catch (e: android.content.ActivityNotFoundException) {
                Log.e(TAG, "ActivityNotFoundException: ${e.message}", e)
                // Fallback: try without chooser
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.d(TAG, "Launched intent directly (fallback after chooser failed)")
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to launch intent: ${e2.message}", e2)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error launching browser: ${e.message}", e)
                e.printStackTrace()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open authorization URL: ${e.message}", e)
            e.printStackTrace()
        }
    }
}
