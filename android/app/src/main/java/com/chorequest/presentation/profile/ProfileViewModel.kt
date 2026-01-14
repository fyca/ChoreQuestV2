package com.chorequest.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.local.SessionManager
import com.chorequest.data.repository.UserRepository
import com.chorequest.domain.models.User
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
}
