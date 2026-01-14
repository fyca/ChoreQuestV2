package com.chorequest.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.local.SessionManager
import com.chorequest.data.repository.UserRepository
import com.chorequest.domain.models.User
import com.chorequest.domain.models.UserRole
import com.chorequest.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for user management
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    private val _userDetailState = MutableStateFlow<UserDetailState>(UserDetailState.Loading)
    val userDetailState: StateFlow<UserDetailState> = _userDetailState.asStateFlow()

    private val _createUserState = MutableStateFlow<CreateUserState>(CreateUserState.Idle)
    val createUserState: StateFlow<CreateUserState> = _createUserState.asStateFlow()

    private val _qrCodeState = MutableStateFlow<QRCodeState>(QRCodeState.Idle)
    val qrCodeState: StateFlow<QRCodeState> = _qrCodeState.asStateFlow()

    val currentUserId: String? 
        get() = sessionManager.loadSession()?.userId

    init {
        loadAllUsers()
    }

    /**
     * Load all family members
     */
    fun loadAllUsers() {
        viewModelScope.launch {
            userRepository.getAllUsers().collect { users ->
                _allUsers.value = users
            }
        }
    }

    /**
     * Load user details
     */
    fun loadUserDetail(userId: String) {
        viewModelScope.launch {
            _userDetailState.value = UserDetailState.Loading
            val user = userRepository.getUserById(userId)
            if (user != null) {
                _userDetailState.value = UserDetailState.Success(user)
            } else {
                _userDetailState.value = UserDetailState.Error("User not found")
            }
        }
    }

    /**
     * Create new user (family member)
     */
    fun createUser(
        name: String,
        role: UserRole,
        canEarnPoints: Boolean,
        avatarUrl: String?
    ) {
        viewModelScope.launch {
            _createUserState.value = CreateUserState.Loading

            userRepository.createUser(name, role, canEarnPoints, avatarUrl).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _createUserState.value = CreateUserState.Success(result.data)
                    }
                    is Result.Error -> {
                        _createUserState.value = CreateUserState.Error(result.message)
                    }
                    is Result.Loading -> {
                        _createUserState.value = CreateUserState.Loading
                    }
                }
            }
        }
    }

    /**
     * Generate QR code for a user
     */
    fun generateQRCode(userId: String) {
        viewModelScope.launch {
            _qrCodeState.value = QRCodeState.Loading

            userRepository.generateQRCode(userId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _qrCodeState.value = QRCodeState.Success(result.data)
                    }
                    is Result.Error -> {
                        _qrCodeState.value = QRCodeState.Error(result.message)
                    }
                    is Result.Loading -> {
                        _qrCodeState.value = QRCodeState.Loading
                    }
                }
            }
        }
    }

    /**
     * Regenerate QR code for a user (invalidates old codes)
     */
    fun regenerateQRCode(userId: String) {
        viewModelScope.launch {
            _qrCodeState.value = QRCodeState.Loading

            userRepository.regenerateQRCode(userId).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _qrCodeState.value = QRCodeState.Success(result.data)
                    }
                    is Result.Error -> {
                        _qrCodeState.value = QRCodeState.Error(result.message)
                    }
                    is Result.Loading -> {
                        _qrCodeState.value = QRCodeState.Loading
                    }
                }
            }
        }
    }

    /**
     * Delete user
     */
    fun deleteUser(user: User) {
        viewModelScope.launch {
            userRepository.deleteUser(user).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _userDetailState.value = UserDetailState.Deleted
                    }
                    is Result.Error -> {
                        _userDetailState.value = UserDetailState.Error(result.message)
                    }
                    is Result.Loading -> {
                        // Keep current state
                    }
                }
            }
        }
    }

    /**
     * Reset create user state
     */
    fun resetCreateUserState() {
        _createUserState.value = CreateUserState.Idle
    }

    /**
     * Reset QR code state
     */
    fun resetQRCodeState() {
        _qrCodeState.value = QRCodeState.Idle
    }
}

/**
 * State for user detail screen
 */
sealed class UserDetailState {
    object Loading : UserDetailState()
    data class Success(val user: User) : UserDetailState()
    data class Error(val message: String) : UserDetailState()
    object Deleted : UserDetailState()
}

/**
 * State for creating users
 */
sealed class CreateUserState {
    object Idle : CreateUserState()
    object Loading : CreateUserState()
    data class Success(val user: User) : CreateUserState()
    data class Error(val message: String) : CreateUserState()
}

/**
 * State for QR code generation
 */
sealed class QRCodeState {
    object Idle : QRCodeState()
    object Loading : QRCodeState()
    data class Success(val qrCodeData: String) : QRCodeState()
    data class Error(val message: String) : QRCodeState()
}
