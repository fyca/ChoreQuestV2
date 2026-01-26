package com.lostsierra.chorequest.services

import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
        gameId: String? = null,
        photoUrl: String? = null
    ) {
        val channelId = when (type) {
            Constants.NotificationTypes.CHORE_COMPLETED,
            Constants.NotificationTypes.CHORE_VERIFIED,
            Constants.NotificationTypes.CHORE_ASSIGNED -> Constants.NotificationChannels.CHORE_UPDATES
            Constants.NotificationTypes.POINTS_AWARDED,
            Constants.NotificationTypes.POINTS_EARNED -> Constants.NotificationChannels.POINTS_UPDATES
            Constants.NotificationTypes.GAME_MOVE -> Constants.NotificationChannels.GAME_UPDATES
            else -> Constants.NotificationChannels.DEFAULT
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            choreId?.let { putExtra("choreId", it) }
            gameId?.let { putExtra("gameId", it) }
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
        
        // Generate a unique notification ID (use hash of title + message to avoid duplicates)
        // Use absolute value and modulo to ensure positive ID within reasonable range
        val notificationId = Math.abs((title + message + (gameId ?: "")).hashCode()) % Int.MAX_VALUE
        
        try {
            Log.d(TAG, "Attempting to show notification: id=$notificationId, channel=$channelId, type=$type")
            Log.d(TAG, "Android SDK version: ${Build.VERSION.SDK_INT}, TIRAMISU=${Build.VERSION_CODES.TIRAMISU}")
            
            // Check notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.d(TAG, "Checking POST_NOTIFICATIONS permission (Android 13+)")
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                
                Log.d(TAG, "POST_NOTIFICATIONS permission granted: $hasPermission")
                if (!hasPermission) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted - cannot show notification")
                    Log.w(TAG, "User needs to grant notification permission in app settings")
                    return
                }
            }
            
            // Check if notifications are enabled for the app
            Log.d(TAG, "Checking if notifications are enabled (Android N+)")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val notificationsEnabled = notificationManager.areNotificationsEnabled()
                Log.d(TAG, "Notifications enabled: $notificationsEnabled")
                if (!notificationsEnabled) {
                    Log.w(TAG, "Notifications are disabled for the app - cannot show notification")
                    return
                }
            }
            
            // Check channel importance (Android O+)
            Log.d(TAG, "Checking notification channel (Android O+)")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Getting notification channel: $channelId")
                val channel = notificationManager.getNotificationChannel(channelId)
                if (channel != null) {
                    Log.d(TAG, "Channel $channelId importance: ${channel.importance}, name: ${channel.name}")
                    if (channel.importance == AndroidNotificationManager.IMPORTANCE_NONE) {
                        Log.w(TAG, "Notification channel $channelId has IMPORTANCE_NONE - notifications won't appear")
                        return
                    }
                } else {
                    Log.e(TAG, "Notification channel $channelId not found - channel may not be created")
                    // Try to create it again
                    createNotificationChannels()
                    val retryChannel = notificationManager.getNotificationChannel(channelId)
                    if (retryChannel == null) {
                        Log.e(TAG, "Failed to create notification channel $channelId")
                        return
                    }
                }
            }
            
            Log.d(TAG, "Building notification and calling notify()")
            val notification = notificationBuilder.build()
            notificationManager.notify(notificationId, notification)
            Log.i(TAG, "Notification sent successfully: id=$notificationId, channel=$channelId, title='$title', message='$message'")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when showing notification - permission may be denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
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
    
    /**
     * Check for game move notifications and display them
     * Called when game state changes are detected during sync
     */
    suspend fun checkAndDisplayGameNotifications(
        previousGames: Map<String, com.lostsierra.chorequest.presentation.games.RemoteGameState>,
        currentGames: Map<String, com.lostsierra.chorequest.presentation.games.RemoteGameState>
    ) {
        try {
            val session = authRepository.getCurrentSession()
            if (session == null) {
                Log.d(TAG, "No session for checking game notifications")
                return
            }
            
            val users = userRepository.getAllUsers().first()
            val currentUser = users.find { it.id == session.userId }
            if (currentUser == null) {
                Log.d(TAG, "Current user not found for checking game notifications")
                return
            }
            
            // Check if notifications are enabled
            if (!currentUser.settings.notifications) {
                Log.d(TAG, "Notifications disabled for user, skipping game notifications")
                return
            }
            
            val currentUserId = session.userId
            Log.d(TAG, "Checking game notifications: ${previousGames.size} previous, ${currentGames.size} current games")
            
            var gamesInProgress = 0
            var userGamesInProgress = 0
            var notificationsSent = 0
            
            // Check each game for changes
            currentGames.forEach { (gameId, currentGame) ->
                val previousGame = previousGames[gameId]
                
                // Count games in progress
                if (!currentGame.isGameOver) {
                    gamesInProgress++
                }
                
                // Check if it's a game the current user is playing
                val isPlayerInGame = currentGame.player1Id == currentUserId || currentGame.player2Id == currentUserId
                if (!isPlayerInGame) {
                    return@forEach
                }
                
                // Count user's games in progress
                if (!currentGame.isGameOver) {
                    userGamesInProgress++
                }
                
                // Determine which player the current user is
                val isPlayerX = currentGame.player1Id == currentUserId
                val myPlayer = if (isPlayerX) "X" else "O"
                val opponentName = if (isPlayerX) currentGame.player2Name else currentGame.player1Name
                
                // Check if it's now the current user's turn
                val isMyTurn = currentGame.currentPlayer == myPlayer && !currentGame.isGameOver
                
                Log.d(TAG, "Game $gameId: isGameOver=${currentGame.isGameOver}, isMyTurn=$isMyTurn, myPlayer=$myPlayer, currentPlayer=${currentGame.currentPlayer}")
                
                // Check if this is a new game (not in previous state)
                if (previousGame == null) {
                    // For new games, check if it's the user's turn and opponent has already moved
                    val moveCount = currentGame.board.count { it != null }
                    val recentlyUpdated = (System.currentTimeMillis() - currentGame.lastUpdated) < 5 * 60 * 1000 // 5 minutes
                    
                    // Only treat as "new game" if there's exactly 1 move (the first move by the game starter)
                    // If there are 2+ moves, it means someone already made a move, so treat it as a move notification
                    if (isMyTurn && !currentGame.isGameOver && moveCount > 0 && recentlyUpdated) {
                        if (moveCount == 1) {
                            // Exactly 1 move - this is a new game
                            Log.i(TAG, "Sending notification for new game $gameId: $opponentName created a game, it's your turn!")
                            showNotification(
                                title = "Your Turn! 🎮",
                                message = "$opponentName started a Tic-Tac-Toe game. It's your turn!",
                                type = Constants.NotificationTypes.GAME_MOVE,
                                gameId = gameId
                            )
                        } else {
                            // 2+ moves - this is a move, not a new game
                            Log.i(TAG, "Sending notification for game $gameId: $opponentName made a move, it's your turn! (moveCount=$moveCount)")
                            showNotification(
                                title = "Your Turn! 🎮",
                                message = "$opponentName made a move in Tic-Tac-Toe. It's your turn!",
                                type = Constants.NotificationTypes.GAME_MOVE,
                                gameId = gameId
                            )
                        }
                        notificationsSent++
                    } else {
                        Log.d(TAG, "Game $gameId: New game, skipping notification (isMyTurn=$isMyTurn, moveCount=$moveCount, recentlyUpdated=$recentlyUpdated)")
                    }
                    return@forEach
                }
                
                // At this point, previousGame is guaranteed to be non-null
                val prevGame = previousGame!!
                
                // Check if game just ended and opponent won
                val gameJustEnded = !prevGame.isGameOver && currentGame.isGameOver
                val opponentWon = gameJustEnded && 
                                 currentGame.winner != null && 
                                 currentGame.winner != myPlayer
                
                if (opponentWon) {
                    // Game just ended and opponent won - send notification
                    val timeSinceLastUpdate = System.currentTimeMillis() - currentGame.lastUpdated
                    val recentlyUpdated = timeSinceLastUpdate < 5 * 60 * 1000 // 5 minutes
                    
                    if (recentlyUpdated) {
                        Log.i(TAG, "Sending notification for game $gameId: $opponentName won!")
                        showNotification(
                            title = "Game Over",
                            message = "$opponentName won the Tic-Tac-Toe game!",
                            type = Constants.NotificationTypes.GAME_MOVE,
                            gameId = gameId
                        )
                        notificationsSent++
                        return@forEach // Don't check for move notifications if game ended
                    }
                }
                
                // Check if the game state changed (new move made)
                val previousMoveCount = prevGame.board.count { it != null }
                val currentMoveCount = currentGame.board.count { it != null }
                val moveCountIncreased = currentMoveCount > previousMoveCount
                
                // Check if board actually changed (more robust than just move count)
                val boardChanged = prevGame.board != currentGame.board
                
                // Check if game was recently updated (within last sync interval)
                val timeSinceLastUpdate = System.currentTimeMillis() - currentGame.lastUpdated
                val recentlyUpdated = timeSinceLastUpdate < 5 * 60 * 1000 // 5 minutes
                
                // Check if game was updated since previous state (indicates activity)
                val gameWasUpdated = currentGame.lastUpdated > prevGame.lastUpdated
                val timeDifference = currentGame.lastUpdated - prevGame.lastUpdated
                val significantTimePassed = timeDifference > 10 * 1000 // 10 seconds (more lenient)
                
                // Check previous turn state
                val previousWasMyTurn = prevGame.currentPlayer == myPlayer && !prevGame.isGameOver
                val previousWasOpponentTurn = prevGame.currentPlayer != myPlayer && !prevGame.isGameOver
                val previousPlayer = prevGame.currentPlayer
                val currentPlayer = currentGame.currentPlayer
                
                // Check if current player changed (indicates a move was made)
                val currentPlayerChanged = previousPlayer != currentPlayer
                
                // Check if it became the current user's turn
                // This happens when: opponent was playing before, now it's user's turn
                val becameMyTurn = isMyTurn && previousWasOpponentTurn
                
                // Also check if player changed from opponent to user (most reliable indicator)
                val opponentToUserTransition = previousPlayer != myPlayer && currentPlayer == myPlayer && !currentGame.isGameOver
                
                // Determine if a move was made (either move count increased, board changed, or player changed)
                val moveMade = moveCountIncreased || (boardChanged && currentPlayerChanged) || currentPlayerChanged
                
                Log.d(TAG, "Game $gameId: previousMoves=$previousMoveCount, currentMoves=$currentMoveCount")
                Log.d(TAG, "Game $gameId: moveCountIncreased=$moveCountIncreased, boardChanged=$boardChanged, currentPlayerChanged=$currentPlayerChanged, moveMade=$moveMade")
                Log.d(TAG, "Game $gameId: previousPlayer=$previousPlayer, currentPlayer=$currentPlayer, myPlayer=$myPlayer")
                Log.d(TAG, "Game $gameId: previousWasMyTurn=$previousWasMyTurn, previousWasOpponentTurn=$previousWasOpponentTurn, isMyTurn=$isMyTurn")
                Log.d(TAG, "Game $gameId: becameMyTurn=$becameMyTurn, opponentToUserTransition=$opponentToUserTransition")
                Log.d(TAG, "Game $gameId: recentlyUpdated=$recentlyUpdated (${timeSinceLastUpdate / 1000}s ago)")
                Log.d(TAG, "Game $gameId: gameWasUpdated=$gameWasUpdated, significantTimePassed=$significantTimePassed (${timeDifference / 1000}s difference)")
                
                // Key insight: If the game was recently updated and it's now the user's turn,
                // but the previous state also showed it as the user's turn, this likely means:
                // 1. Opponent made a move (which changed currentPlayer from X to O)
                // 2. Previous cache was stored AFTER that move (so it already shows O)
                // 3. Current state also shows O (user's turn)
                // 
                // We should notify if:
                // - Game was updated recently (within sync window) - this is the key indicator
                // - It's the user's turn now
                // - Previous state also had user's turn (meaning cache was stored after opponent's move)
                // - Game is in progress
                // - At least one move has been made (game has started)
                val isStaleCacheCase = recentlyUpdated && isMyTurn && previousWasMyTurn && 
                                      !currentGame.isGameOver && currentMoveCount > 0 &&
                                      timeSinceLastUpdate < 3 * 60 * 1000 // Within 3 minutes (sync window)
                
                // Another case: Game was recently updated, a move was made, and it's currently showing
                // opponent's turn, but based on move count it should actually be user's turn.
                // This handles cases where the game state might be slightly out of sync.
                // After an opponent's move, it should be user's turn (alternating turns).
                val shouldBeMyTurnAfterOpponentMove = recentlyUpdated && !isMyTurn && 
                                                     currentPlayer != myPlayer && 
                                                     moveMade && !currentGame.isGameOver &&
                                                     currentMoveCount > 0 &&
                                                     timeSinceLastUpdate < 3 * 60 * 1000 &&
                                                     // If opponent just moved, next should be user's turn
                                                     previousWasOpponentTurn
                
                // Special case: Game was recently updated (within sync window), it's showing opponent's turn,
                // and previous state also showed opponent's turn. This means the opponent likely just made
                // a move, and it should now be the user's turn (even though the game state might not
                // reflect this yet, or the previous cache was stored after the move).
                // We check if the game was updated within the last sync interval to catch this.
                val opponentJustMovedCase = recentlyUpdated && !isMyTurn && 
                                          currentPlayer != myPlayer && 
                                          previousWasOpponentTurn &&
                                          !currentGame.isGameOver &&
                                          currentMoveCount > 0 &&
                                          timeSinceLastUpdate < 3 * 60 * 1000 &&
                                          // Game was updated recently (within sync window)
                                          timeSinceLastUpdate < 5 * 60 * 1000
                
                // Show notification if:
                // 1. It's now the user's turn AND
                // 2. The game is not over AND
                // 3. Either:
                //    a) A move was made (move count increased OR board/player changed), OR
                //    b) Player transitioned from opponent to user (most reliable), OR
                //    c) Stale cache case: game was updated recently, it's user's turn, but previous also showed user's turn, OR
                //    d) Opponent just moved: game updated recently, move made, should be user's turn next, OR
                //    e) Opponent just moved (special case): game updated recently, showing opponent's turn, should be user's turn
                val shouldNotify = (!currentGame.isGameOver) && 
                                  ((isMyTurn && (moveMade || opponentToUserTransition || becameMyTurn || isStaleCacheCase)) ||
                                   shouldBeMyTurnAfterOpponentMove ||
                                   opponentJustMovedCase)
                
                if (shouldBeMyTurnAfterOpponentMove) {
                    Log.d(TAG, "Game $gameId: Detected opponent move case - should notify even though isMyTurn=false")
                }
                
                if (opponentJustMovedCase) {
                    Log.d(TAG, "Game $gameId: Detected opponent just moved case - game updated recently, should be user's turn")
                }
                
                if (isStaleCacheCase) {
                    Log.d(TAG, "Game $gameId: Detected stale cache case - game updated recently, notifying anyway")
                }
                
                if (shouldNotify) {
                    Log.i(TAG, "Sending notification for game $gameId: $opponentName made a move, it's your turn!")
                    showNotification(
                        title = "Your Turn! 🎮",
                        message = "$opponentName made a move in Tic-Tac-Toe. It's your turn!",
                        type = Constants.NotificationTypes.GAME_MOVE,
                        gameId = gameId
                    )
                    notificationsSent++
                } else {
                    Log.d(TAG, "Game $gameId: No notification needed (moveMade=$moveMade, isMyTurn=$isMyTurn, becameMyTurn=$becameMyTurn, shouldBeMyTurnAfterOpponentMove=$shouldBeMyTurnAfterOpponentMove, opponentJustMovedCase=$opponentJustMovedCase, isGameOver=${currentGame.isGameOver})")
                }
            }
            
            Log.i(TAG, "Game notification check completed: $gamesInProgress total games in progress, $userGamesInProgress user games in progress, $notificationsSent notifications sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking game notifications", e)
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
                },
                NotificationChannel(
                    Constants.NotificationChannels.GAME_UPDATES,
                    "Game Updates",
                    AndroidNotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications about game moves and turns"
                }
            )
            
            channels.forEach { channel ->
                try {
                    notificationManager.createNotificationChannel(channel)
                    Log.d(TAG, "Created notification channel: ${channel.id} with importance ${channel.importance}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create notification channel: ${channel.id}", e)
                }
            }
        }
    }
}
