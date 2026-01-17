package com.chorequest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.chorequest.data.local.SessionManager
import com.chorequest.data.repository.UserRepository
import com.chorequest.presentation.navigation.NavigationGraph
import com.chorequest.presentation.theme.AppTheme
import com.chorequest.presentation.theme.ChoreQuestTheme
import com.chorequest.presentation.theme.toAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var sessionManager: SessionManager
    
    @Inject
    lateinit var userRepository: UserRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
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
                    NavigationGraph(navController = navController)
                }
            }
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

