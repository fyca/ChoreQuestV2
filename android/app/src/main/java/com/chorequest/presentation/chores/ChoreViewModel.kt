package com.chorequest.presentation.chores

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.local.SessionManager
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
import kotlinx.coroutines.flow.*
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
                
                val response = api.uploadPhoto(
                    request = PhotoUploadRequest(
                        base64Data = base64Data,
                        fileName = fileName,
                        mimeType = "image/jpeg",
                        choreId = choreId,
                        userId = session?.userId
                    )
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val photoUrl = response.body()!!.webViewLink ?: response.body()!!.url
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
     * Complete chore with optional photo
     */
    fun completeChore(choreId: String, photoUri: Uri? = null) {
        viewModelScope.launch {
            val session = sessionManager.loadSession() ?: return@launch
            
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
