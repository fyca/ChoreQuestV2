package com.lostsierra.chorequest.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostsierra.chorequest.data.local.SessionManager
import com.lostsierra.chorequest.data.repository.UserRepository
import com.lostsierra.chorequest.domain.models.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for profile screen
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _userState = MutableStateFlow<User?>(null)
    val userState: StateFlow<User?> = _userState.asStateFlow()

    init {
        loadCurrentUser()
    }

    /**
     * Load current user from session
     */
    private fun loadCurrentUser() {
        viewModelScope.launch {
            val session = sessionManager.loadSession()
            if (session != null) {
                // Get user from local database
                val user = userRepository.getUserById(session.userId)
                if (user != null) {
                    _userState.value = user
                } else {
                    // If user not in local DB, try to get from all users
                    userRepository.getAllUsers().collect { users ->
                        val foundUser = users.find { it.id == session.userId }
                        if (foundUser != null) {
                            _userState.value = foundUser
                        }
                    }
                }
            }
        }
    }

    /**
     * Refresh user data
     */
    fun refresh() {
        loadCurrentUser()
    }

    private val _updateProfileState = MutableStateFlow<UpdateProfileState>(UpdateProfileState.Idle)
    val updateProfileState: StateFlow<UpdateProfileState> = _updateProfileState.asStateFlow()

    /**
     * Update user profile
     */
    fun updateProfile(
        name: String? = null,
        avatarUrl: String? = null,
        settings: com.lostsierra.chorequest.domain.models.UserSettings? = null
    ) {
        viewModelScope.launch {
            val currentUser = _userState.value
            if (currentUser != null) {
                _updateProfileState.value = UpdateProfileState.Loading
                userRepository.updateUser(
                    userId = currentUser.id,
                    name = name,
                    avatarUrl = avatarUrl,
                    settings = settings
                ).collect { result ->
                    when (result) {
                        is com.lostsierra.chorequest.utils.Result.Success -> {
                            _userState.value = result.data
                            _updateProfileState.value = UpdateProfileState.Success
                        }
                        is com.lostsierra.chorequest.utils.Result.Error -> {
                            _updateProfileState.value = UpdateProfileState.Error(result.message)
                        }
                        is com.lostsierra.chorequest.utils.Result.Loading -> {
                            _updateProfileState.value = UpdateProfileState.Loading
                        }
                    }
                }
            } else {
                _updateProfileState.value = UpdateProfileState.Error("User not found")
            }
        }
    }

    /**
     * Reset update profile state
     */
    fun resetUpdateProfileState() {
        _updateProfileState.value = UpdateProfileState.Idle
    }
}

/**
 * UI state for update profile operation
 */
sealed class UpdateProfileState {
    object Idle : UpdateProfileState()
    object Loading : UpdateProfileState()
    object Success : UpdateProfileState()
    data class Error(val message: String) : UpdateProfileState()
}
