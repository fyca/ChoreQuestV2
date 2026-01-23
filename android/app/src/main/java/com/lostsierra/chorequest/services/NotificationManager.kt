package com.lostsierra.chorequest.services

import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lostsierra.chorequest.MainActivity
import com.lostsierra.chorequest.R
import com.lostsierra.chorequest.data.repository.AuthRepository
import com.lostsierra.chorequest.data.repository.UserRepository
import com.lostsierra.chorequest.domain.models.UserRole
import com.lostsierra.chorequest.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
    
    companion object {
        private const val TAG = "NotificationManager"
    }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Show a local notification
     */
    suspend fun showNotification(
        title: String,
        message: String,
        type: String = Constants.NotificationTypes.CHORE_COMPLETED,
        choreId: String? = null,
        photoUrl: String? = null
    ) {
        val channelId = when (type) {
            Constants.NotificationTypes.CHORE_COMPLETED,
            Constants.NotificationTypes.CHORE_VERIFIED,
            Constants.NotificationTypes.CHORE_ASSIGNED -> Constants.NotificationChannels.CHORE_UPDATES
            Constants.NotificationTypes.POINTS_AWARDED,
            Constants.NotificationTypes.POINTS_EARNED -> Constants.NotificationChannels.POINTS_UPDATES
            else -> Constants.NotificationChannels.DEFAULT
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            choreId?.let { putExtra("choreId", it) }
            putExtra("notificationType", type)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        
        // Load and add photo if available
        val photoBitmap = photoUrl?.let { url ->
            try {
                loadImageBitmap(url)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load image for notification: ${e.message}")
                null
            }
        }
        
        // Use BigPictureStyle if we have a photo
        photoBitmap?.let { bitmap ->
            val bigPictureStyle = NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .bigLargeIcon(null as Bitmap?) // Hide large icon when expanded
                .setSummaryText(message)
            
            notificationBuilder
                .setStyle(bigPictureStyle)
                .setLargeIcon(bitmap) // Show photo as large icon
        }
        
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
    
    /**
     * Load image bitmap from URL
     * Handles both proxy URLs (base64) and regular image URLs
     */
    private suspend fun loadImageBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (url.contains("path=photo")) {
                // Proxy URL - fetch base64 data
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.readBytes()
                    val responseString = String(response)
                    
                    // Check if response is base64 encoded
                    if (responseString.startsWith("data:image") || responseString.contains("base64")) {
                        val base64Data = responseString.substringAfter("base64,")
                        val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    } else {
                        // Try decoding as direct image bytes
                        BitmapFactory.decodeByteArray(response, 0, response.size)
                    }
                } else {
                    Log.e(TAG, "Failed to fetch image from proxy, response code: $responseCode")
                    null
                }
            } else {
                // Regular URL - download image directly
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val inputStream = connection.inputStream
                    BitmapFactory.decodeStream(inputStream)
                } else {
                    Log.e(TAG, "Failed to fetch image, response code: $responseCode")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image bitmap: ${e.message}", e)
            null
        }
    }
    
    /**
     * Check for pending notifications and display them
     * Called during sync when chore status changes are detected
     */
    suspend fun checkAndDisplayNotifications(
        previousChores: List<com.lostsierra.chorequest.domain.models.Chore>,
        currentChores: List<com.lostsierra.chorequest.domain.models.Chore>
    ) {
        try {
            val session = authRepository.getCurrentSession()
            if (session == null) return
            
            val users = userRepository.getAllUsers().first()
            val currentUser = users.find { it.id == session.userId }
            if (currentUser == null) return
            
            // Check if notifications are enabled
            if (!currentUser.settings.notifications) {
                return
            }
            
            val previousChoresMap = previousChores.associateBy { it.id }
            
            currentChores.forEach { currentChore ->
                val previousChore = previousChoresMap[currentChore.id]
                
                if (previousChore == null) {
                    // New chore added
                    val isAssignedToUser = currentChore.assignedTo.contains(currentUser.id)
                    val isUnassigned = currentChore.assignedTo.isEmpty()
                    
                    // Notify if:
                    // 1. Chore is assigned to current user, OR
                    // 2. Chore is unassigned and user is a child (children can complete unassigned chores)
                    if (isAssignedToUser || (isUnassigned && currentUser.role == UserRole.CHILD)) {
                        val points = currentChore.pointValue
                        val dueDateText = currentChore.dueDate?.let { dueDate ->
                            try {
                                // Extract date part from ISO string (YYYY-MM-DD)
                                val datePart = dueDate.substring(0, minOf(10, dueDate.length))
                                " (Due: $datePart)"
                            } catch (e: Exception) {
                                ""
                            }
                        } ?: ""
                        
                        val message = buildString {
                            append(currentChore.title)
                            if (points > 0) {
                                append(" - $points points")
                            }
                            append(dueDateText)
                        }
                        
                        showNotification(
                            title = "New Chore Assigned! 📋",
                            message = message,
                            type = Constants.NotificationTypes.CHORE_ASSIGNED,
                            choreId = currentChore.id
                        )
                    }
                } else {
                    // Chore status changed
                    when {
                        // Child completed a chore - notify parents
                        currentChore.status == com.lostsierra.chorequest.domain.models.ChoreStatus.COMPLETED &&
                        previousChore.status != com.lostsierra.chorequest.domain.models.ChoreStatus.COMPLETED &&
                        currentUser.role == UserRole.PARENT -> {
                            val completedBy = currentChore.completedBy
                            val completedByUser = users.find { it.id == completedBy }
                            val childName = completedByUser?.name ?: "Someone"
                            
                            // Get photo proof URL if available
                            val photoUrl = currentChore.photoProof?.let { photoProof ->
                                // Convert Drive URL to proxy URL if needed (similar to ChoreDetailScreen)
                                if (photoProof.contains("drive.google.com")) {
                                    try {
                                        var fileId: String? = null
                                        val pattern1 = Regex("/file/d/([a-zA-Z0-9_-]+)").find(photoProof)
                                        if (pattern1 != null) {
                                            fileId = pattern1.groupValues[1]
                                        }
                                        if (fileId == null) {
                                            val pattern2 = Regex("[?&]id=([a-zA-Z0-9_-]+)").find(photoProof)
                                            if (pattern2 != null) {
                                                fileId = pattern2.groupValues[1]
                                            }
                                        }
                                        if (fileId != null) {
                                            val primaryParent = users.find { it.isPrimaryParent }
                                            val ownerEmail = primaryParent?.email
                                            if (ownerEmail != null) {
                                                "${Constants.APPS_SCRIPT_WEB_APP_URL}?path=photo&fileId=$fileId&ownerEmail=$ownerEmail"
                                            } else {
                                                photoProof
                                            }
                                        } else {
                                            photoProof
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error converting photo URL: ${e.message}")
                                        photoProof
                                    }
                                } else {
                                    photoProof
                                }
                            }
                            
                            val messageText = if (photoUrl != null) {
                                "$childName completed: ${currentChore.title} 📸"
                            } else {
                                "$childName completed: ${currentChore.title}"
                            }
                            
                            showNotification(
                                title = "Chore Completed!",
                                message = messageText,
                                type = Constants.NotificationTypes.CHORE_COMPLETED,
                                choreId = currentChore.id,
                                photoUrl = photoUrl
                            )
                        }
                        
                        // Chore was verified - notify child
                        currentChore.status == com.lostsierra.chorequest.domain.models.ChoreStatus.VERIFIED &&
                        previousChore.status == com.lostsierra.chorequest.domain.models.ChoreStatus.COMPLETED &&
                        currentUser.role == UserRole.CHILD &&
                        currentChore.completedBy == currentUser.id -> {
                            val points = currentChore.pointValue
                            
                            showNotification(
                                title = "Chore Verified! 🎉",
                                message = "You earned $points points for: ${currentChore.title}",
                                type = Constants.NotificationTypes.CHORE_VERIFIED,
                                choreId = currentChore.id
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notifications", e)
        }
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    Constants.NotificationChannels.DEFAULT,
                    "General Notifications",
                    AndroidNotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "General app notifications"
                },
                NotificationChannel(
                    Constants.NotificationChannels.CHORE_UPDATES,
                    "Chore Updates",
                    AndroidNotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications about chore completions and verifications"
                },
                NotificationChannel(
                    Constants.NotificationChannels.POINTS_UPDATES,
                    "Points Updates",
                    AndroidNotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications about points earned"
                }
            )
            
            channels.forEach { channel ->
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
