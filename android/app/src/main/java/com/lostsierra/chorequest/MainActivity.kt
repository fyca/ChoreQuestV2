package com.lostsierra.chorequest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.lostsierra.chorequest.data.local.SessionManager
import com.lostsierra.chorequest.data.repository.UserRepository
import com.lostsierra.chorequest.presentation.navigation.NavigationGraph
import com.lostsierra.chorequest.presentation.theme.AppTheme
import com.lostsierra.chorequest.presentation.theme.ChoreQuestTheme
import com.lostsierra.chorequest.presentation.theme.toAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var sessionManager: SessionManager
    
    @Inject
    lateinit var userRepository: UserRepository
    
    private val lifecycleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var hasHandledForeground = false
    
    // Permission launcher for notification permission (Android 13+)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "Notification permission granted")
        } else {
            android.util.Log.w("MainActivity", "Notification permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request notification permission if needed (Android 13+)
        requestNotificationPermissionIfNeeded()
        
        // Handle notification intents
        handleNotificationIntent(intent)
        
        // Observe lifecycle to detect when app comes to foreground
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_RESUME && !hasHandledForeground) {
                    // App came to foreground - update login streak
                    hasHandledForeground = true
                    lifecycleScope.launch {
                        updateLoginStreak()
                    }
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    // Reset flag when app goes to background
                    hasHandledForeground = false
                }
            }
        })
        
        setContent {
            // Load current user's theme
            val currentTheme = rememberCurrentUserTheme(sessionManager, userRepository)
            val darkTheme = isSystemInDarkTheme()
            
            ChoreQuestTheme(
                themeMode = currentTheme,
                darkTheme = darkTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val gameId = remember { intent.getStringExtra("gameId") }
                    
                    // Store gameId in ViewModel if present
                    if (gameId != null) {
                        androidx.compose.runtime.LaunchedEffect(gameId) {
                            // We'll need to access TicTacToeViewModel here, but it's not available yet
                            // Instead, we'll pass it through navigation and handle it in TicTacToeScreen
                        }
                    }
                    
                    NavigationGraph(
                        navController = navController,
                        initialGameId = gameId
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleNotificationIntent(it) }
    }
    
    private fun handleNotificationIntent(intent: Intent?) {
        intent?.let {
            val notificationType = it.getStringExtra("notificationType")
            val gameId = it.getStringExtra("gameId")
            
            if (notificationType == com.lostsierra.chorequest.utils.Constants.NotificationTypes.GAME_MOVE && gameId != null) {
                // Store gameId to be used by NavigationGraph
                // The gameId will be passed to NavigationGraph via initialGameId parameter
            }
        }
    }
    
    /**
     * Request notification permission if needed (Android 13+)
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    android.util.Log.d("MainActivity", "Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // User has denied permission before, show rationale if needed
                    // For now, just request again
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Request permission
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    
    /**
     * Update login streak when app comes to foreground
     */
    private suspend fun updateLoginStreak() {
        val session = sessionManager.loadSession()
        if (session != null) {
            userRepository.updateLoginStreak(session.userId)
        }
    }
}

@Composable
private fun rememberCurrentUserTheme(
    sessionManager: SessionManager,
    userRepository: UserRepository
): AppTheme {
    val session = remember { sessionManager.loadSession() }
    
    val theme = if (session != null) {
        // Observe user changes reactively using Flow
        val allUsers by userRepository.getAllUsers().collectAsState(initial = emptyList())
        val user = allUsers.find { it.id == session.userId }
        
        user?.settings?.theme?.toAppTheme() ?: AppTheme.SYSTEM
    } else {
        AppTheme.SYSTEM
    }
    
    return theme
}

