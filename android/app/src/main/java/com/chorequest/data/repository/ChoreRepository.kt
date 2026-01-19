package com.chorequest.data.repository

import android.util.Log
import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.ChoreDao
import com.chorequest.data.local.dao.UserDao
import com.chorequest.data.local.entities.toEntity
import com.chorequest.data.local.entities.toDomain
import com.chorequest.data.remote.*
import com.chorequest.domain.models.Chore
import com.chorequest.domain.models.ChoreStatus
import com.chorequest.domain.models.ChoreTemplate
import com.chorequest.domain.models.ChoresData
import com.chorequest.domain.models.TemplatesData
import com.chorequest.utils.Result
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChoreRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val choreDao: ChoreDao,
    private val sessionManager: SessionManager,
    private val gson: Gson,
    private val driveApiService: com.chorequest.data.drive.DriveApiService,
    private val tokenManager: com.chorequest.data.drive.TokenManager,
    private val userDao: com.chorequest.data.local.dao.UserDao
) {
    companion object {
        private const val TAG = "ChoreRepository"
    }
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Get all chores - loads from Drive on-demand, then returns from local cache
     */
    fun getAllChores(): Flow<List<Chore>> {
        // Trigger background load from Drive (non-blocking)
        repositoryScope.launch {
            loadChoresFromDrive()
        }
        
        // Return from local cache immediately, then update when Drive data loads
        return choreDao.getAllChores().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Load chores from Drive and update local cache
     */
    private suspend fun loadChoresFromDrive() {
        try {
            val session = sessionManager.loadSession() ?: return
            val accessToken = tokenManager.getValidAccessToken()
            
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Loading chores from Drive on-demand")
                    val folderId = session.driveWorkbookLink
                    val choresData = readChoresFromDrive(accessToken, folderId)
                    
                    if (choresData != null) {
                        val chores = choresData.chores
                        // Update local cache
                        choreDao.deleteAllChores()
                        if (chores.isNotEmpty()) {
                            choreDao.insertChores(chores.map { it.toEntity() })
                        }
                        Log.d(TAG, "Loaded ${chores.size} chores from Drive")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading chores from Drive, using local cache", e)
                }
            } else {
                Log.d(TAG, "No access token, using local cache only")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadChoresFromDrive", e)
        }
    }

    /**
     * Get chores for specific user
     */
    fun getChoresForUser(userId: String): Flow<List<Chore>> {
        return choreDao.getChoresForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get chore by ID
     */
    suspend fun getChoreById(choreId: String): Chore? {
        return choreDao.getChoreById(choreId)?.toDomain()
    }

    /**
     * Helper: Read chores from Drive using direct API
     */
    private suspend fun readChoresFromDrive(accessToken: String, folderId: String): ChoresData? {
        return try {
            val fileName = "chores.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, ChoresData::class.java)
            } else {
                // File doesn't exist, return empty
                ChoresData(chores = emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading chores from Drive", e)
            null
        }
    }

    /**
     * Helper: Write chores to Drive using direct API
     */
    private suspend fun writeChoresToDrive(accessToken: String, folderId: String, choresData: ChoresData): Boolean {
        return try {
            val fileName = "chores.json"
            val jsonContent = gson.toJson(choresData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing chores to Drive", e)
            false
        }
    }

    /**
     * Helper: Read templates from Drive using direct API
     */
    private suspend fun readTemplatesFromDrive(accessToken: String, folderId: String): TemplatesData? {
        return try {
            val fileName = "recurring_chore_templates.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, TemplatesData::class.java)
            } else {
                // File doesn't exist, return empty
                TemplatesData(templates = emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading templates from Drive", e)
            null
        }
    }

    /**
     * Helper: Write templates to Drive using direct API
     */
    private suspend fun writeTemplatesToDrive(accessToken: String, folderId: String, templatesData: TemplatesData): Boolean {
        return try {
            val fileName = "recurring_chore_templates.json"
            val jsonContent = gson.toJson(templatesData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing templates to Drive", e)
            false
        }
    }

    /**
     * Create new chore
     * Drive is the source of truth - save to Drive FIRST, then cache locally
     */
    fun createChore(chore: Chore): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to create chore")
                    val folderId = session.driveWorkbookLink
                    
                    var templateSaved = true
                    
                    // If recurring, create template first
                    if (chore.recurring != null) {
                        Log.d(TAG, "Creating recurring chore - saving template")
                        val templatesData = readTemplatesFromDrive(accessToken, folderId) ?: TemplatesData(templates = emptyList())
                        
                        // Create template from chore
                        val template = ChoreTemplate(
                            id = java.util.UUID.randomUUID().toString(),
                            title = chore.title,
                            description = chore.description,
                            assignedTo = chore.assignedTo,
                            createdBy = chore.createdBy,
                            pointValue = chore.pointValue,
                            dueDate = chore.dueDate,
                            recurring = chore.recurring,
                            subtasks = chore.subtasks,
                            createdAt = java.time.Instant.now().toString(),
                            color = chore.color,
                            icon = chore.icon
                        )
                        
                        // Add template to list
                        val currentTemplates = templatesData.templates ?: emptyList()
                        val updatedTemplates = currentTemplates + template
                        val updatedTemplatesData = templatesData.copy(templates = updatedTemplates)
                        
                        // Save template to Drive
                        templateSaved = writeTemplatesToDrive(accessToken, folderId, updatedTemplatesData)
                        if (!templateSaved) {
                            Log.e(TAG, "Failed to write template to Drive")
                        } else {
                            Log.d(TAG, "Template saved successfully: ${template.id}")
                        }
                    }
                    
                    // Read current chores
                    val choresData = readChoresFromDrive(accessToken, folderId) ?: ChoresData(chores = emptyList())
                    
                    // Add new chore
                    val updatedChores = choresData.chores + chore
                    val updatedData = choresData.copy(chores = updatedChores)
                    
                    // Write back to Drive
                    val choreSaved = writeChoresToDrive(accessToken, folderId, updatedData)
                    
                    // For recurring chores, both template and chore must be saved
                    if (chore.recurring != null) {
                        if (templateSaved && choreSaved) {
                            // Update local cache
                            choreDao.insertChore(chore.toEntity())
                            Log.d(TAG, "Recurring chore and template created successfully via Drive API: ${chore.id}")
                            emit(Result.Success(chore))
                            return@flow
                        } else {
                            Log.w(TAG, "Failed to save recurring chore (template=$templateSaved, chore=$choreSaved), falling back to Apps Script")
                        }
                    } else {
                        // Non-recurring chore - only chore needs to be saved
                        if (choreSaved) {
                            // Update local cache
                            choreDao.insertChore(chore.toEntity())
                            Log.d(TAG, "Chore created successfully via Drive API: ${chore.id}")
                            emit(Result.Success(chore))
                            return@flow
                        } else {
                            Log.w(TAG, "Failed to write chore to Drive, falling back to Apps Script")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            }

            // Fallback to Apps Script
            val request = CreateChoreRequest(
                creatorId = session.userId,
                title = chore.title,
                description = chore.description,
                assignedTo = chore.assignedTo,
                pointValue = chore.pointValue,
                dueDate = chore.dueDate,
                recurring = chore.recurring,
                subtasks = chore.subtasks,
                color = null, // TODO: Add color/icon support
                icon = null
            )

            val response = api.createChore(request = request)
            
            // Check for authorization error (401 status code)
            if (!response.isSuccessful && response.code() == 401) {
                val authUrl = com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
                Log.e(TAG, "Authorization required: $errorMsg")
                emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg"))
                return@flow
            }
            
            if (response.isSuccessful && response.body()?.success == true) {
                val createdChore = response.body()?.chore
                if (createdChore != null) {
                    // Only save to local cache AFTER successful Drive save
                    choreDao.insertChore(createdChore.toEntity())
                    Log.d(TAG, "Chore created successfully on Drive: ${createdChore.id}")
                    emit(Result.Success(createdChore))
                } else {
                    Log.w(TAG, "Drive created chore but returned null chore")
                    emit(Result.Error("Chore created but response was invalid"))
                }
            } else {
                val errorBody = response.body()
                val errorMsg = errorBody?.error 
                    ?: errorBody?.message 
                    ?: response.message() 
                    ?: "Failed to create chore on Drive"
                
                Log.d(TAG, "Error response: code=${response.code()}, errorMsg=$errorMsg")
                
                // Check if error message indicates authorization is needed
                // Also check response code again in case it's 401 but we got here
                if (response.code() == 401 || com.chorequest.utils.AuthorizationHelper.isAuthorizationError(errorMsg)) {
                    val authUrl = com.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(errorMsg)
                        ?: com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                    val userFriendlyMsg = com.chorequest.utils.AuthorizationHelper.extractErrorMessage(errorMsg)
                    Log.e(TAG, "Authorization required (from error message): $userFriendlyMsg")
                    emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$userFriendlyMsg"))
                } else {
                    Log.e(TAG, "Failed to create chore on Drive: $errorMsg")
                    emit(Result.Error("Failed to save chore to Drive: $errorMsg"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating chore", e)
            emit(Result.Error(e.message ?: "Failed to create chore"))
        }
    }

    /**
     * Update existing chore
     * Drive is the source of truth - save to Drive FIRST, then cache locally
     */
    fun updateChore(chore: Chore): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to update chore")
                    val folderId = session.driveWorkbookLink
                    
                    // Read current chores
                    val choresData = readChoresFromDrive(accessToken, folderId)
                    if (choresData != null) {
                        // Find and update the chore
                        val updatedChores = choresData.chores.map { 
                            if (it.id == chore.id) chore else it
                        }
                        val updatedData = choresData.copy(chores = updatedChores)
                        
                        // Write back to Drive
                        if (writeChoresToDrive(accessToken, folderId, updatedData)) {
                            // Update local cache
                            choreDao.updateChore(chore.toEntity())
                            Log.d(TAG, "Chore updated successfully via Drive API: ${chore.id}")
                            emit(Result.Success(chore))
                            return@flow
                        } else {
                            Log.w(TAG, "Failed to write chore update to Drive, falling back to Apps Script")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            }

            // Fallback to Apps Script
            val updates = ChoreUpdates(
                title = chore.title,
                description = chore.description,
                assignedTo = chore.assignedTo,
                pointValue = chore.pointValue,
                dueDate = chore.dueDate,
                recurring = chore.recurring,
                subtasks = chore.subtasks,
                color = null,
                icon = null
            )

            val request = UpdateChoreRequest(
                userId = session.userId,
                choreId = chore.id,
                updates = updates
            )

            val response = api.updateChore(request = request)
            
            // Check for authorization error (401 status code)
            if (!response.isSuccessful && response.code() == 401) {
                val authUrl = com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
                Log.e(TAG, "Authorization required: $errorMsg")
                emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg"))
                return@flow
            }
            
            if (response.isSuccessful && response.body()?.success == true) {
                val updatedChore = response.body()?.chore
                if (updatedChore != null) {
                    // Only update local cache AFTER successful Drive save
                    choreDao.updateChore(updatedChore.toEntity())
                    Log.d(TAG, "Chore updated successfully on Drive: ${updatedChore.id}")
                    emit(Result.Success(updatedChore))
                } else {
                    Log.w(TAG, "Drive updated chore but returned null chore")
                    emit(Result.Error("Chore updated but response was invalid"))
                }
            } else {
                val errorBody = response.body()
                val errorMsg = errorBody?.error 
                    ?: errorBody?.message 
                    ?: response.message() 
                    ?: "Failed to update chore on Drive"
                
                // Check if error message indicates authorization is needed
                if (com.chorequest.utils.AuthorizationHelper.isAuthorizationError(errorMsg)) {
                    val authUrl = com.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(errorMsg)
                        ?: com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                    val userFriendlyMsg = com.chorequest.utils.AuthorizationHelper.extractErrorMessage(errorMsg)
                    Log.e(TAG, "Authorization required (from error message): $userFriendlyMsg")
                    emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$userFriendlyMsg"))
                } else {
                    Log.e(TAG, "Failed to update chore on Drive: $errorMsg")
                    emit(Result.Error("Failed to save chore update to Drive: $errorMsg"))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions - they should propagate up
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error updating chore", e)
            emit(Result.Error(e.message ?: "Failed to update chore"))
        }
    }

    /**
     * Delete chore
     * Drive is the source of truth - delete from Drive FIRST, then remove from local cache
     */
    fun deleteChore(chore: Chore): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to delete chore")
                    val folderId = session.driveWorkbookLink
                    
                    // Read current chores
                    val choresData = readChoresFromDrive(accessToken, folderId)
                    if (choresData != null) {
                        // Remove the chore
                        val updatedChores = choresData.chores.filter { it.id != chore.id }
                        val updatedData = choresData.copy(chores = updatedChores)
                        
                        // Write back to Drive
                        if (writeChoresToDrive(accessToken, folderId, updatedData)) {
                            // Remove from local cache
                            choreDao.deleteChore(chore.toEntity())
                            Log.d(TAG, "Chore deleted successfully via Drive API: ${chore.id}")
                            emit(Result.Success(Unit))
                            return@flow
                        } else {
                            Log.w(TAG, "Failed to delete chore from Drive, falling back to Apps Script")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            }

            // Fallback to Apps Script
            val request = DeleteChoreRequest(
                userId = session.userId,
                choreId = chore.id
            )

            val response = api.deleteChore(request = request)
            
            // Check for authorization error (401 status code)
            if (!response.isSuccessful && response.code() == 401) {
                val authUrl = com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
                Log.e(TAG, "Authorization required: $errorMsg")
                emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg"))
                return@flow
            }
            
            if (response.isSuccessful && response.body()?.success == true) {
                // Only remove from local cache AFTER successful Drive deletion
                choreDao.deleteChore(chore.toEntity())
                Log.d(TAG, "Chore deleted successfully on Drive: ${chore.id}")
                emit(Result.Success(Unit))
            } else {
                val errorBody = response.body()
                val errorMsg = errorBody?.error 
                    ?: errorBody?.message 
                    ?: response.message() 
                    ?: "Failed to delete chore on Drive"
                
                // Check if error message indicates authorization is needed
                if (com.chorequest.utils.AuthorizationHelper.isAuthorizationError(errorMsg)) {
                    val authUrl = com.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(errorMsg)
                        ?: com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                    val userFriendlyMsg = com.chorequest.utils.AuthorizationHelper.extractErrorMessage(errorMsg)
                    Log.e(TAG, "Authorization required (from error message): $userFriendlyMsg")
                    emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$userFriendlyMsg"))
                } else {
                    Log.e(TAG, "Failed to delete chore on Drive: $errorMsg")
                    emit(Result.Error("Failed to delete chore from Drive: $errorMsg"))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions - they should propagate up
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting chore", e)
            emit(Result.Error(e.message ?: "Failed to delete chore"))
        }
    }

    /**
     * Complete chore
     * Drive is the source of truth - save to Drive FIRST, then cache locally
     */
    fun completeChore(choreId: String, userId: String, photoProof: String? = null): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to complete chore")
                    val folderId = session.driveWorkbookLink
                    
                    // Read current chores
                    val choresData = readChoresFromDrive(accessToken, folderId)
                    if (choresData != null) {
                        // Find and update the chore
                        val chore = choresData.chores.find { it.id == choreId }
                        if (chore != null) {
                            val now = java.time.Instant.now().toString()
                            val completedChore = chore.copy(
                                status = ChoreStatus.COMPLETED,
                                completedBy = userId,
                                completedAt = now,
                                photoProof = photoProof ?: chore.photoProof
                            )
                            
                            val updatedChores = choresData.chores.map { 
                                if (it.id == choreId) completedChore else it
                            }
                            val updatedData = choresData.copy(chores = updatedChores)
                            
                            // Write back to Drive
                            if (writeChoresToDrive(accessToken, folderId, updatedData)) {
                                // Update local cache
                                choreDao.updateChore(completedChore.toEntity())
                                Log.d(TAG, "Chore completed successfully via Drive API: $choreId")
                                emit(Result.Success(completedChore))
                                return@flow
                            } else {
                                Log.w(TAG, "Failed to complete chore on Drive, falling back to Apps Script")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            }

            // Fallback to Apps Script
            val request = CompleteChoreRequest(
                userId = userId,
                choreId = choreId,
                photoProof = photoProof
            )

            val response = api.completeChore(request = request)
            
            // Check for authorization error (401 status code)
            if (!response.isSuccessful && response.code() == 401) {
                val authUrl = com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
                Log.e(TAG, "Authorization required: $errorMsg")
                emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg"))
                return@flow
            }
            
            if (response.isSuccessful && response.body()?.success == true) {
                val completedChore = response.body()?.chore
                if (completedChore != null) {
                    // Only update local cache AFTER successful Drive save
                    choreDao.updateChore(completedChore.toEntity())
                    Log.d(TAG, "Chore completed successfully on Drive: $choreId")
                    emit(Result.Success(completedChore))
                } else {
                    Log.w(TAG, "Drive completed chore but returned null chore")
                    emit(Result.Error("Chore completed but response was invalid"))
                }
            } else {
                val errorBody = response.body()
                val errorMsg = errorBody?.error 
                    ?: errorBody?.message 
                    ?: response.message() 
                    ?: "Failed to complete chore on Drive"
                
                // Check if error message indicates authorization is needed
                if (com.chorequest.utils.AuthorizationHelper.isAuthorizationError(errorMsg)) {
                    val authUrl = com.chorequest.utils.AuthorizationHelper.extractAuthorizationUrl(errorMsg)
                        ?: com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
                    val userFriendlyMsg = com.chorequest.utils.AuthorizationHelper.extractErrorMessage(errorMsg)
                    Log.e(TAG, "Authorization required (from error message): $userFriendlyMsg")
                    emit(Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$userFriendlyMsg"))
                } else {
                    Log.e(TAG, "Failed to complete chore on Drive: $errorMsg")
                    emit(Result.Error("Failed to save chore completion to Drive: $errorMsg"))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Re-throw cancellation exceptions - they should propagate up
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error completing chore", e)
            emit(Result.Error(e.message ?: "Failed to complete chore"))
        }
    }

    /**
     * Verify a completed chore (parent action)
     */
    fun verifyChore(choreId: String, approved: Boolean): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to verify chore")
                    val folderId = session.driveWorkbookLink
                    
                    // Read current chores
                    val choresData = readChoresFromDrive(accessToken, folderId)
                    if (choresData != null) {
                        // Find and update the chore
                        val chore = choresData.chores.find { it.id == choreId }
                        if (chore != null) {
                            val now = java.time.Instant.now().toString()
                            val verifiedChore = if (approved) {
                                chore.copy(
                                    status = ChoreStatus.VERIFIED,
                                    verifiedBy = session.userId,
                                    verifiedAt = now
                                )
                            } else {
                                // Reject: set back to pending
                                chore.copy(
                                    status = ChoreStatus.PENDING,
                                    completedBy = null,
                                    completedAt = null,
                                    photoProof = null,
                                    verifiedBy = null,
                                    verifiedAt = null
                                )
                            }
                            
                            val updatedChores = choresData.chores.map { 
                                if (it.id == choreId) verifiedChore else it
                            }
                            val updatedData = choresData.copy(chores = updatedChores)
                            
                            // Write back to Drive
                            if (writeChoresToDrive(accessToken, folderId, updatedData)) {
                                // Update local cache
                                choreDao.updateChore(verifiedChore.toEntity())
                                Log.d(TAG, "Chore verified successfully via Drive API: $choreId")
                                emit(Result.Success(verifiedChore))
                                return@flow
                            } else {
                                Log.w(TAG, "Failed to verify chore on Drive, falling back to Apps Script")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            }

            // Fallback to Apps Script
            val response = api.verifyChore(
                request = VerifyChoreRequest(
                    parentId = session.userId,
                    choreId = choreId,
                    approved = approved
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val updated = response.body()?.chore
                if (updated != null) {
                    // Update local cache
                    choreDao.updateChore(updated.toEntity())
                    emit(Result.Success(updated))
                } else {
                    emit(Result.Error("Verify succeeded but chore was missing in response"))
                }
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to verify chore"
                emit(Result.Error(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying chore", e)
            emit(Result.Error(e.message ?: "Failed to verify chore"))
        }
    }

    /**
     * Sync chores from backend
     */
    suspend fun syncChores() {
        try {
            // TODO: Fetch from backend and update local database
        } catch (e: Exception) {
            // Handle error
        }
    }

    /**
     * Get recurring chore templates
     * Uses direct Drive API for faster performance (no cold start)
     */
    fun getRecurringChoreTemplates(): Flow<List<ChoreTemplate>> = flow {
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                Log.w(TAG, "No session found for fetching templates")
                emit(emptyList())
                return@flow
            }

            // Try to get access token for direct Drive API
            val accessToken = tokenManager.getValidAccessToken()
            
            if (accessToken != null) {
                // Use direct Drive API
                try {
                    Log.d(TAG, "Using direct Drive API to fetch templates")
                    val folderId = session.driveWorkbookLink
                    val fileName = "recurring_chore_templates.json"
                    
                    // Find file in folder
                    val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
                    
                    if (fileId != null) {
                        // Read file content
                        val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                        val templatesData = gson.fromJson(jsonContent, TemplatesData::class.java)
                        val templates = templatesData.templates ?: emptyList()
                        Log.d(TAG, "Fetched ${templates.size} templates via Drive API")
                        emit(templates)
                    } else {
                        Log.d(TAG, "Template file not found, returning empty list")
                        emit(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                    // Fall back to Apps Script
                    val templates = fetchTemplatesViaAppsScript(session.familyId)
                    emit(templates)
                }
            } else {
                // Fall back to Apps Script if no token available
                Log.d(TAG, "No access token available, using Apps Script")
                val templates = fetchTemplatesViaAppsScript(session.familyId)
                emit(templates)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching templates", e)
            emit(emptyList())
        }
    }
    
    /**
     * Fallback: Fetch templates via Apps Script
     */
    private suspend fun fetchTemplatesViaAppsScript(familyId: String): List<ChoreTemplate> {
        val response = api.getData(
            path = "data",
            action = "get",
            type = "recurring_chore_templates",
            familyId = familyId
        )

        Log.d(TAG, "Template fetch response - Success: ${response.isSuccessful}, Code: ${response.code()}")
        
        if (response.isSuccessful && response.body()?.success == true) {
            val data = response.body()?.data
            Log.d(TAG, "Template data received: ${data != null}")
            
            if (data != null) {
                try {
                    val jsonString = gson.toJson(data)
                    Log.d(TAG, "Template JSON string length: ${jsonString.length}")
                    val templatesData = gson.fromJson(jsonString, TemplatesData::class.java)
                    val templates = templatesData.templates ?: emptyList()
                    Log.d(TAG, "Parsed ${templates.size} templates")
                    return templates
                } catch (parseError: Exception) {
                    Log.e(TAG, "Error parsing templates data", parseError)
                    Log.e(TAG, "Data structure: ${gson.toJson(data)}")
                    return emptyList()
                }
            } else {
                Log.w(TAG, "No data in response body")
                return emptyList()
            }
        } else {
            val errorBody = response.body()
            val errorMsg = errorBody?.error ?: errorBody?.message ?: "Unknown error"
            Log.e(TAG, "Failed to fetch templates: $errorMsg")
            Log.e(TAG, "Response code: ${response.code()}, Body: ${errorBody}")
            return emptyList()
        }
    }

    /**
     * Delete recurring chore template
     * Uses direct Drive API for faster performance (no cold start)
     */
    fun deleteRecurringChoreTemplate(templateId: String): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Try to get access token for direct Drive API
            val accessToken = tokenManager.getValidAccessToken()
            
            if (accessToken != null) {
                // Use direct Drive API
                try {
                    Log.d(TAG, "Using direct Drive API to delete template")
                    val folderId = session.driveWorkbookLink
                    val fileName = "recurring_chore_templates.json"
                    
                    // Read current templates
                    val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
                    if (fileId == null) {
                        emit(Result.Error("Template file not found"))
                        return@flow
                    }
                    
                    val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                    val templatesData = gson.fromJson(jsonContent, TemplatesData::class.java)
                    val templates = (templatesData.templates ?: emptyList()).toMutableList()
                    
                    // Remove template
                    templates.removeAll { it.id == templateId }
                    
                    // Update file
                    val updatedData = TemplatesData(templates = templates, metadata = templatesData.metadata)
                    val updatedJson = gson.toJson(updatedData)
                    driveApiService.writeFileContent(accessToken, folderId, fileName, updatedJson)
                    
                    Log.d(TAG, "Template deleted successfully via Drive API")
                    emit(Result.Success(Unit))
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                    // Fall back to Apps Script
                    val result = deleteTemplateViaAppsScript(session.userId, templateId)
                    emit(result)
                }
            } else {
                // Fall back to Apps Script if no token available
                Log.d(TAG, "No access token available, using Apps Script")
                val result = deleteTemplateViaAppsScript(session.userId, templateId)
                emit(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting template", e)
            emit(Result.Error(e.message ?: "Failed to delete template"))
        }
    }
    
    /**
     * Fallback: Delete template via Apps Script
     */
    private suspend fun deleteTemplateViaAppsScript(userId: String, templateId: String): Result<Unit> {
        val request = DeleteTemplateRequest(
            userId = userId,
            templateId = templateId
        )

        val response = api.deleteRecurringChoreTemplate(request = request)
        
        // Check for authorization error (401 status code)
        if (!response.isSuccessful && response.code() == 401) {
            val authUrl = com.chorequest.utils.AuthorizationHelper.getBaseAuthorizationUrl()
            val errorMsg = "Drive access not authorized. Please authorize the app to access your Google Drive."
            Log.e(TAG, "Authorization required: $errorMsg")
            return Result.Error("AUTHORIZATION_REQUIRED:$authUrl:$errorMsg")
        }
        
        if (response.isSuccessful && response.body()?.success == true) {
            Log.d(TAG, "Template deleted successfully on Drive: $templateId")
            return Result.Success(Unit)
        } else {
            val errorBody = response.body()
            val errorMsg = errorBody?.error ?: errorBody?.message ?: "Unknown error"
            Log.e(TAG, "Failed to delete template: $errorMsg")
            return Result.Error(errorMsg)
        }
    }
}

