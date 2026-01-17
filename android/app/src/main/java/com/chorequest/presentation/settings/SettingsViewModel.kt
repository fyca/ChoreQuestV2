package com.chorequest.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.local.SessionManager
import com.chorequest.data.repository.DataRepository
import com.chorequest.data.repository.UserRepository
import com.chorequest.domain.models.User
import com.chorequest.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for settings screen
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val dataRepository: DataRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    val isPrimaryParent: StateFlow<Boolean> = _currentUser
        .map { it?.isPrimaryParent ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _deleteAllDataState = MutableStateFlow<DeleteAllDataState>(DeleteAllDataState.Idle)
    val deleteAllDataState: StateFlow<DeleteAllDataState> = _deleteAllDataState.asStateFlow()

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
                    _currentUser.value = user
                } else {
                    // If user not in local DB, try to get from all users
                    userRepository.getAllUsers().firstOrNull()?.let { users ->
                        val foundUser = users.find { it.id == session.userId }
                        if (foundUser != null) {
                            _currentUser.value = foundUser
                        }
                    }
                }
            }
        }
    }

    /**
     * Delete all user data (only accessible by primary parent)
     */
    fun deleteAllData() {
        viewModelScope.launch {
            _deleteAllDataState.value = DeleteAllDataState.Loading
            try {
                dataRepository.deleteAllData().collect { result ->
                    when (result) {
                        is Result.Success -> {
                            _deleteAllDataState.value = DeleteAllDataState.Success
                        }
                        is Result.Error -> {
                            _deleteAllDataState.value = DeleteAllDataState.Error(result.message)
                        }
                        is Result.Loading -> {
                            // Already set to loading
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Operation was cancelled - set error state
                _deleteAllDataState.value = DeleteAllDataState.Error("Operation was cancelled. Please try again.")
            } catch (e: Exception) {
                _deleteAllDataState.value = DeleteAllDataState.Error("Error: ${e.message}")
            }
        }
    }

    /**
     * Reset delete state
     */
    fun resetDeleteState() {
        _deleteAllDataState.value = DeleteAllDataState.Idle
    }
}

/**
 * UI state for delete all data operation
 */
sealed class DeleteAllDataState {
    object Idle : DeleteAllDataState()
    object Loading : DeleteAllDataState()
    object Success : DeleteAllDataState()
    data class Error(val message: String) : DeleteAllDataState()
}
