package com.chorequest.presentation.chores

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.UserDao
import com.chorequest.data.local.entities.toDomain
import com.chorequest.data.remote.ChoreQuestApi
import com.chorequest.data.remote.PhotoUploadRequest
import com.chorequest.data.repository.ChoreRepository
import com.chorequest.domain.models.*
import com.chorequest.utils.Constants
import com.chorequest.utils.ImageUtils
import com.chorequest.utils.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for chore management
 */
@HiltViewModel
class ChoreViewModel @Inject constructor(
    private val choreRepository: ChoreRepository,
    private val sessionManager: SessionManager,
    private val api: ChoreQuestApi,
    private val userDao: UserDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _allChores = MutableStateFlow<List<Chore>>(emptyList())
    val allChores: StateFlow<List<Chore>> = _allChores.asStateFlow()

    private val _choreDetailState = MutableStateFlow<ChoreDetailState>(ChoreDetailState.Loading)
    val choreDetailState: StateFlow<ChoreDetailState> = _choreDetailState.asStateFlow()

    private val _createEditState = MutableStateFlow<CreateEditState>(CreateEditState.Idle)
    val createEditState: StateFlow<CreateEditState> = _createEditState.asStateFlow()
    
    private val _uploadProgress = MutableStateFlow<UploadProgress>(UploadProgress.Idle)
    val uploadProgress: StateFlow<UploadProgress> = _uploadProgress.asStateFlow()

    val currentUserId: String? 
        get() = sessionManager.loadSession()?.userId

    init {
        loadAllChores()
    }

    /**
     * Load all chores
     */
    fun loadAllChores() {
        viewModelScope.launch {
            choreRepository.getAllChores().collect { chores ->
                _allChores.value = chores
            }
        }
        // Fetch debug logs in background
        viewModelScope.launch {
            choreRepository.fetchDebugLogs()
        }
    }

    /**
     * Load chore details
     */
    fun loadChoreDetail(choreId: String) {
        viewModelScope.launch {
            _choreDetailState.value = ChoreDetailState.Loading
            val chore = choreRepository.getChoreById(choreId)
            if (chore != null) {
                _choreDetailState.value = ChoreDetailState.Success(chore)
            } else {
                _choreDetailState.value = ChoreDetailState.Error("Chore not found")
            }
        }
    }

    /**
     * Create new chore
     */
    fun createChore(
        title: String,
        description: String,
        assignedTo: List<String>,
        pointValue: Int,
        dueDate: String?,
        subtasks: List<Subtask>,
        color: String?,
        icon: String?,
        recurring: RecurringSchedule? = null
    ) {
        viewModelScope.launch {
            val session = sessionManager.loadSession() ?: return@launch

            val newChore = Chore(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                assignedTo = assignedTo,
                createdBy = session.userId,
                pointValue = pointValue,
                dueDate = dueDate,
                recurring = recurring,
                subtasks = subtasks,
                status = ChoreStatus.PENDING,
                createdAt = Instant.now().toString()
            )

            _createEditState.value = CreateEditState.Loading

            choreRepository.createChore(newChore).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _createEditState.value = CreateEditState.Success("Chore created successfully!")
                        // Reload chores to show the new one
                        loadAllChores()
                    }
                    is Result.Error -> {
                        android.util.Log.d("ChoreViewModel", "Error received: ${result.message}")
                        // Check if this is an authorization error
                        val isAuthError = com.chorequest.utils.AuthorizationHelper.isAuthorizationError(result.message)
                        android.util.Log.d("ChoreViewModel", "Is authorization error: $isAuthError")
                        
                        if (isAuthError) {
                            val authUrl = com.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(result.message)
                                ?: com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                            val errorMsg = com.chorequest.utils.AuthorizationHelper.extractErrorMessage(result.message)
                            android.util.Log.d("ChoreViewModel", "Setting AuthorizationRequired state: url=$authUrl, message=$errorMsg")
                            _createEditState.value = CreateEditState.AuthorizationRequired(authUrl, errorMsg)
                        } else {
                            android.util.Log.d("ChoreViewModel", "Setting Error state: ${result.message}")
                            _createEditState.value = CreateEditState.Error(result.message)
                        }
                    }
                    is Result.Loading -> {
                        _createEditState.value = CreateEditState.Loading
                    }
                }
            }
        }
    }

    /**
     * Update existing chore
     */
    fun updateChore(chore: Chore) {
        viewModelScope.launch {
            _createEditState.value = CreateEditState.Loading

            choreRepository.updateChore(chore).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _createEditState.value = CreateEditState.Success("Chore updated successfully!")
                        // Reload chores to show the updated one
                        loadAllChores()
                    }
                    is Result.Error -> {
                        _createEditState.value = CreateEditState.Error(result.message)
                    }
                    is Result.Loading -> {
                        _createEditState.value = CreateEditState.Loading
                    }
                }
            }
        }
    }

    /**
     * Delete chore
     */
    fun deleteChore(chore: Chore) {
        viewModelScope.launch {
            choreRepository.deleteChore(chore).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _choreDetailState.value = ChoreDetailState.Deleted
                    }
                    is Result.Error -> {
                        _choreDetailState.value = ChoreDetailState.Error(result.message)
                    }
                    is Result.Loading -> {
                        // Keep current state during deletion
                    }
                }
            }
        }
    }

    /**
     * Verify a completed chore (parent action)
     */
    fun verifyChore(choreId: String, approved: Boolean = true) {
        viewModelScope.launch {
            _createEditState.value = CreateEditState.Loading
            choreRepository.verifyChore(choreId = choreId, approved = approved).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _createEditState.value = CreateEditState.Success("Chore verified successfully!")
                        // Refresh chore list and detail
                        loadAllChores()
                        loadChoreDetail(choreId)
                    }
                    is Result.Error -> {
                        _createEditState.value = CreateEditState.Error(result.message)
                    }
                    is Result.Loading -> {
                        _createEditState.value = CreateEditState.Loading
                    }
                }
            }
        }
    }

    /**
     * Complete chore (for children)
     */
    /**
     * Upload photo to Google Drive
     */
    suspend fun uploadPhotoToDrive(photoUri: Uri, choreId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                _uploadProgress.value = UploadProgress.Compressing
                
                // Compress and encode image
                val base64Data = ImageUtils.compressAndEncodeImage(context, photoUri)
                if (base64Data == null) {
                    _uploadProgress.value = UploadProgress.Error("Failed to compress image")
                    return@withContext Result.Error("Failed to compress image")
                }
                
                _uploadProgress.value = UploadProgress.Uploading(0)
                
                // Upload to Drive via Apps Script
                val fileName = "chore_${choreId}_${System.currentTimeMillis()}.jpg"
                val session = sessionManager.loadSession()
                
                // Get primary parent's email (ownerEmail)
                val allUsers = userDao.getAllUsers().first()
                val primaryParent = allUsers.find { it.toDomain().isPrimaryParent }
                val ownerEmail = primaryParent?.toDomain()?.email
                    ?: throw Exception("Primary parent not found - cannot upload photo")
                
                val response = api.uploadPhoto(
                    request = PhotoUploadRequest(
                        base64Data = base64Data,
                        fileName = fileName,
                        mimeType = "image/jpeg",
                        choreId = choreId,
                        userId = session?.userId,
                        ownerEmail = ownerEmail
                    )
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    // Use fileId to create a proxy URL through our Apps Script backend
                    // This ensures we get actual image data, not HTML
                    val fileId = response.body()!!.fileId
                    val photoUrl = if (fileId != null && ownerEmail != null) {
                        // Use our backend proxy endpoint to serve the image directly
                        "${com.chorequest.utils.Constants.APPS_SCRIPT_WEB_APP_URL}?path=photo&fileId=$fileId&ownerEmail=$ownerEmail"
                    } else {
                        // Fallback to downloadUrl or webViewLink if fileId is missing
                        response.body()!!.downloadUrl ?: response.body()!!.webViewLink ?: response.body()!!.url
                    }
                    _uploadProgress.value = UploadProgress.Success(photoUrl ?: "")
                    Result.Success(photoUrl ?: "")
                } else {
                    val error = response.body()?.error ?: "Upload failed"
                    _uploadProgress.value = UploadProgress.Error(error)
                    Result.Error(error)
                }
            } catch (e: Exception) {
                val error = "Upload error: ${e.message}"
                _uploadProgress.value = UploadProgress.Error(error)
                Result.Error(error)
            }
        }
    }
    
    /**
     * Complete chore with optional photo and subtasks
     */
    fun completeChore(choreId: String, photoUri: Uri? = null, updatedSubtasks: List<Subtask>? = null) {
        viewModelScope.launch {
            val session = sessionManager.loadSession() ?: return@launch
            
            // If subtasks were updated, save them to the backend first
            if (updatedSubtasks != null) {
                val currentChore = _choreDetailState.value
                if (currentChore is ChoreDetailState.Success) {
                    val chore = currentChore.chore
                    val updatedChore = chore.copy(subtasks = updatedSubtasks)
                    // Update subtasks on backend before completing
                    // Wait for the final result (Success or Error), skipping Loading states
                    var updateCompleted = false
                    var updateError: String? = null
                    choreRepository.updateChore(updatedChore)
                        .filter { it !is Result.Loading }
                        .take(1)
                        .collect { result ->
                            when (result) {
                                is Result.Success -> {
                                    updateCompleted = true
                                }
                                is Result.Error -> {
                                    updateError = result.message
                                    updateCompleted = true
                                }
                                is Result.Loading -> {
                                    // Should not happen after filter
                                }
                            }
                        }
                    if (updateError != null) {
                        _choreDetailState.value = ChoreDetailState.Error("Failed to update subtasks: $updateError")
                        return@launch
                    }
                    if (!updateCompleted) {
                        _choreDetailState.value = ChoreDetailState.Error("Failed to update subtasks: Update did not complete")
                        return@launch
                    }
                }
            }
            
            // Upload photo first if provided
            val photoUrl = if (photoUri != null) {
                when (val uploadResult = uploadPhotoToDrive(photoUri, choreId)) {
                    is Result.Success -> uploadResult.data
                    is Result.Error -> {
                        _choreDetailState.value = ChoreDetailState.Error("Photo upload failed: ${uploadResult.message}")
                        return@launch
                    }
                    is Result.Loading -> null
                }
            } else {
                null
            }

            // Complete chore with photo URL
            choreRepository.completeChore(choreId, session.userId, photoUrl).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _choreDetailState.value = ChoreDetailState.Success(result.data)
                        _uploadProgress.value = UploadProgress.Idle
                    }
                    is Result.Error -> {
                        _choreDetailState.value = ChoreDetailState.Error(result.message)
                        _uploadProgress.value = UploadProgress.Idle
                    }
                    is Result.Loading -> {
                        // Keep current state
                    }
                }
            }
        }
    }
    
    /**
     * Reset upload progress
     */
    fun resetUploadProgress() {
        _uploadProgress.value = UploadProgress.Idle
    }

    /**
     * Reset create/edit state
     */
    fun resetCreateEditState() {
        _createEditState.value = CreateEditState.Idle
    }
}

/**
 * State for chore detail screen
 */
sealed class ChoreDetailState {
    object Loading : ChoreDetailState()
    data class Success(val chore: Chore) : ChoreDetailState()
    data class Error(val message: String) : ChoreDetailState()
    object Deleted : ChoreDetailState()
}

/**
 * State for create/edit operations
 */
sealed class CreateEditState {
    object Idle : CreateEditState()
    object Loading : CreateEditState()
    data class Success(val message: String) : CreateEditState()
    data class Error(val message: String) : CreateEditState()
    data class AuthorizationRequired(val url: String, val message: String) : CreateEditState()
}

/**
 * Upload progress state
 */
sealed class UploadProgress {
    object Idle : UploadProgress()
    object Compressing : UploadProgress()
    data class Uploading(val progress: Int) : UploadProgress()
    data class Success(val url: String) : UploadProgress()
    data class Error(val message: String) : UploadProgress()
}
