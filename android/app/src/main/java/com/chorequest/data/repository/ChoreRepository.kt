package com.chorequest.data.repository

import android.util.Log
import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.ChoreDao
import com.chorequest.data.local.entities.toEntity
import com.chorequest.data.local.entities.toDomain
import com.chorequest.data.remote.*
import com.chorequest.domain.models.Chore
import com.chorequest.utils.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChoreRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val choreDao: ChoreDao,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "ChoreRepository"
    }

    /**
     * Get all chores from local cache
     */
    fun getAllChores(): Flow<List<Chore>> {
        return choreDao.getAllChores().map { entities ->
            entities.map { it.toDomain() }
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

            // Save to Drive FIRST (Drive is source of truth)
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

            // Save to Drive FIRST (Drive is source of truth)
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

            // Delete from Drive FIRST (Drive is source of truth)
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
            // Save to Drive FIRST (Drive is source of truth)
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

            // Call backend (Drive-backed)
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
}
