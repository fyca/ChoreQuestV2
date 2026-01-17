package com.chorequest.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.repository.AuthRepository
import com.chorequest.workers.SyncManager
import com.chorequest.domain.models.User
import com.chorequest.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for login screen
 * Handles Google OAuth and session validation
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Initial)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()
    
    // Store the last Google ID token and email when authorization is required, so we can retry after authorization
    private var pendingGoogleToken: String? = null
    private var pendingGoogleEmail: String? = null
    private var pendingServerAuthCode: String? = null

    init {
        checkExistingSession()
    }

    /**
     * Check if user has existing valid session
     */
    private fun checkExistingSession() {
        if (authRepository.hasValidSession()) {
            viewModelScope.launch {
                _loginState.value = LoginState.Loading
                authRepository.validateSession().collect { result ->
                    when (result) {
                        is Result.Success -> {
                            _loginState.value = LoginState.Success(result.data)
                            // Navigate based on user role
                            _navigationEvent.emit(
                                if (result.data.role == com.chorequest.domain.models.UserRole.PARENT) {
                                    NavigationEvent.NavigateToParentDashboard
                                } else {
                                    NavigationEvent.NavigateToChildDashboard
                                }
                            )
                        }
                        is Result.Error -> {
                            _loginState.value = LoginState.Initial
                        }
                        is Result.Loading -> {
                            _loginState.value = LoginState.Loading
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle Google OAuth login
     */
    fun loginWithGoogle(idToken: String, email: String? = null, accessToken: String? = null, serverAuthCode: String? = null) {
        android.util.Log.d("LoginViewModel", "loginWithGoogle called with token length: ${idToken.length}, accessToken: ${if (accessToken != null) "present" else "null"}, serverAuthCode: ${if (serverAuthCode != null) "present" else "null"}")
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            android.util.Log.d("LoginViewModel", "State set to Loading")
            // Store tokens for retry
            pendingServerAuthCode = serverAuthCode
            authRepository.authenticateWithGoogle(idToken, accessToken, serverAuthCode).collect { result ->
                android.util.Log.d("LoginViewModel", "Auth result received: ${result::class.simpleName}")
                when (result) {
                    is Result.Success -> {
                        android.util.Log.d("LoginViewModel", "Login successful: ${result.data.name}, role=${result.data.role}")
                        _loginState.value = LoginState.Success(result.data)
                        pendingGoogleToken = null // Clear pending token on success
                        pendingServerAuthCode = null // Clear server auth code on success
                        
                        // Trigger immediate sync using SyncManager (won't be cancelled)
                        android.util.Log.d("LoginViewModel", "Triggering immediate sync after login...")
                        syncManager.triggerImmediateSync()
                        
                        // Always navigate to parent dashboard for OAuth users
                        android.util.Log.d("LoginViewModel", "Emitting NavigateToParentDashboard event")
                        _navigationEvent.emit(NavigationEvent.NavigateToParentDashboard)
                    }
                    is Result.Error -> {
                        android.util.Log.e("LoginViewModel", "Login error: ${result.message}")
                        // Check if this is an authorization error
                        if (com.chorequest.utils.AuthorizationHelper.isAuthorizationError(result.message)) {
                            val authUrl = com.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(result.message)
                                ?: com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                            val errorMsg = com.chorequest.utils.AuthorizationHelper.extractErrorMessage(result.message)
                            android.util.Log.d("LoginViewModel", "Setting AuthorizationRequired state")
                            // Store the token, email, and server auth code so we can retry after authorization
                            pendingGoogleToken = idToken
                            pendingGoogleEmail = email
                            // Keep the server auth code for retry
                            // Add email to authorization URL if available
                            val finalAuthUrl = if (email != null) {
                                "$authUrl?email=${java.net.URLEncoder.encode(email, "UTF-8")}"
                            } else {
                                authUrl
                            }
                            _loginState.value = LoginState.AuthorizationRequired(finalAuthUrl, errorMsg)
                        } else {
                            _loginState.value = LoginState.Error(result.message)
                            pendingGoogleToken = null
                            pendingServerAuthCode = null // Clear server auth code on error
                        }
                    }
                    is Result.Loading -> {
                        android.util.Log.d("LoginViewModel", "Loading state")
                        _loginState.value = LoginState.Loading
                    }
                }
            }
        }
    }
    
    /**
     * Retry login with the stored Google token (after authorization)
     */
    fun retryLoginAfterAuthorization() {
        val token = pendingGoogleToken
        val serverAuthCode = pendingServerAuthCode
        if (token != null) {
            android.util.Log.d("LoginViewModel", "Retrying login after authorization with stored token, serverAuthCode: ${if (serverAuthCode != null) "present" else "null"}")
            // Retry without access token - it will need to be re-obtained
            loginWithGoogle(token, pendingGoogleEmail, null, serverAuthCode)
        } else {
            android.util.Log.w("LoginViewModel", "No pending token to retry login")
            _loginState.value = LoginState.Error("No pending login. Please sign in again.")
        }
    }

    /**
     * Navigate to QR scanner
     */
    fun navigateToQRScanner() {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.NavigateToQRScanner)
        }
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _loginState.value = LoginState.Initial
    }
}

/**
 * UI state for login screen
 */
sealed class LoginState {
    object Initial : LoginState()
    object Loading : LoginState()
    data class Success(val user: User) : LoginState()
    data class Error(val message: String) : LoginState()
    data class AuthorizationRequired(val url: String, val message: String) : LoginState()
}

/**
 * Navigation events
 */
sealed class NavigationEvent {
    object NavigateToParentDashboard : NavigationEvent()
    object NavigateToChildDashboard : NavigationEvent()
    object NavigateToQRScanner : NavigationEvent()
}
