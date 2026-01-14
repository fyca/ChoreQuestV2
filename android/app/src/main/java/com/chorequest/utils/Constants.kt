package com.chorequest.utils

/**
 * Shared constants for ChoreQuest Android app
 */

object Constants {
    
    // Google Apps Script Web App URL (full deployment URL)
    // Format: https://script.google.com/macros/s/SCRIPT_ID/exec
    // Note: No trailing slash - Apps Script doesn't work with it, and we'll handle it in Retrofit config
    const val APPS_SCRIPT_WEB_APP_URL = "https://script.google.com/macros/s/AKfycbwpyWr94eySQ8SXnlg2wHIcdum27rpnuzayF7sEt6CdLS8FDNfLf3XBo86eRTERsqou/exec"
    
    // Google OAuth Web Client ID (from Google Cloud Console)
    // IMPORTANT: This MUST be the Web application client ID, NOT the Android client ID
    // Get this from: Google Cloud Console > APIs & Services > Credentials
    const val GOOGLE_WEB_CLIENT_ID = "156195149694-a3c7v365m6a2rhq46icqh1c13oi6r8h2.apps.googleusercontent.com"
    
    // API Endpoints
    object ApiPaths {
        const val AUTH = "/auth"
        const val USERS = "/users"
        const val DATA = "/data"
        const val SYNC = "/sync"
        const val BATCH = "/batch"
    }
    
    // Entity Types
    object EntityTypes {
        const val FAMILY = "family"
        const val USERS = "users"
        const val CHORES = "chores"
        const val REWARDS = "rewards"
        const val TRANSACTIONS = "transactions"
        const val ACTIVITY_LOG = "activity_log"
    }
    
    // Polling Intervals (milliseconds)
    object PollingIntervals {
        const val FOREGROUND_ACTIVE = 30000L        // 30 seconds when app is active
        const val BACKGROUND = 900000L              // 15 minutes when app is in background
        const val SMART_POLLING = 10000L            // 10 seconds for 2 minutes after user action
        const val SMART_POLLING_DURATION = 120000L  // 2 minutes
    }
    
    // Storage Keys
    object StorageKeys {
        const val SESSION = "chorequest_session"
        const val PREFERENCES = "chorequest_preferences"
        const val CACHE_PREFIX = "chorequest_cache_"
    }
    
    // QR Code
    object QrCode {
        const val URI_SCHEME = "chorequest://auth"
        const val VERSION = "1.0.0"
    }
    
    // Validation
    object Validation {
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_NAME_LENGTH = 50
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_CHORE_TITLE_LENGTH = 100
        const val MIN_POINTS = 1
        const val MAX_POINTS = 1000
        const val MAX_SUBTASKS = 20
    }
    
    // Points
    object Points {
        const val DEFAULT_CHORE_VALUE = 10
        const val MIN_VALUE = 1
        const val MAX_VALUE = 1000
        const val DEFAULT_MULTIPLIER = 1.0
    }
    
    // Activity Log
    object ActivityLog {
        const val PAGE_SIZE = 50
        const val DEFAULT_RETENTION_DAYS = 365
        const val MAX_LOGS = 1000
    }
    
    // Colors
    object ChoreColors {
        val colors = listOf(
            Pair("Red", "#E74C3C"),
            Pair("Orange", "#E67E22"),
            Pair("Yellow", "#F39C12"),
            Pair("Green", "#27AE60"),
            Pair("Blue", "#3498DB"),
            Pair("Purple", "#8E44AD"),
            Pair("Pink", "#E91E63")
        )
    }
    
    // Status Colors
    object StatusColors {
        const val PENDING = "#95A5A6"
        const val IN_PROGRESS = "#3498DB"
        const val COMPLETED = "#27AE60"
        const val VERIFIED = "#16A085"
        const val OVERDUE = "#E74C3C"
    }
    
    // Notification Types
    object NotificationTypes {
        const val CHORE_DUE_SOON = "chore_due_soon"
        const val CHORE_OVERDUE = "chore_overdue"
        const val CHORE_ASSIGNED = "chore_assigned"
        const val CHORE_COMPLETED = "chore_completed"
        const val POINTS_EARNED = "points_earned"
        const val REWARD_REDEEMED = "reward_redeemed"
    }
    
    // Notification Channels
    object NotificationChannels {
        const val CHORES_CHANNEL_ID = "chores_channel"
        const val REWARDS_CHANNEL_ID = "rewards_channel"
        const val SYNC_CHANNEL_ID = "sync_channel"
    }
    
    // App Version
    const val APP_VERSION = "1.0.0"
    
    // Error Messages
    object ErrorMessages {
        const val NETWORK_ERROR = "Network error. Please check your connection."
        const val AUTH_FAILED = "Authentication failed. Please try again."
        const val SESSION_EXPIRED = "Your session has expired. Please scan your QR code again."
        const val QR_INVALID = "Invalid QR code. Please ask your parent for a new one."
        const val PERMISSION_DENIED = "You do not have permission to perform this action."
        const val UNKNOWN_ERROR = "An unexpected error occurred. Please try again."
    }
    
    // Success Messages
    object SuccessMessages {
        const val CHORE_COMPLETED = "Great job! Chore completed! 🎉"
        const val REWARD_REDEEMED = "Reward redeemed successfully!"
        const val SAVED = "Changes saved successfully."
    }
    
    // Preferences
    object PreferenceKeys {
        const val THEME_MODE = "theme_mode"
        const val CELEBRATION_STYLE = "celebration_style"
        const val SOUND_EFFECTS = "sound_effects"
        const val NOTIFICATIONS_ENABLED = "notifications_enabled"
    }
    
    // WorkManager Tags
    object WorkManagerTags {
        const val SYNC_WORK = "sync_work"
        const val POLL_WORK = "poll_work"
    }
}
