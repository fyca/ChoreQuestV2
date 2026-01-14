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
     */
    fun createChore(chore: Chore): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Save to local database first (optimistic update)
            choreDao.insertChore(chore.toEntity())

            // Sync with backend
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
            
            if (response.isSuccessful && response.body()?.success == true) {
                val createdChore = response.body()?.chore
                if (createdChore != null) {
                    // Update local database with server response (may have different ID)
                    choreDao.deleteChore(chore.toEntity())
                    choreDao.insertChore(createdChore.toEntity())
                    Log.d(TAG, "Chore created successfully on backend: ${createdChore.id}")
                    emit(Result.Success(createdChore))
                } else {
                    Log.w(TAG, "Backend created chore but returned null chore")
                    emit(Result.Success(chore))
                }
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to create chore on backend"
                Log.e(TAG, "Failed to create chore on backend: $errorMsg")
                // Keep local data even if backend sync fails
                emit(Result.Success(chore))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating chore", e)
            emit(Result.Error(e.message ?: "Failed to create chore"))
        }
    }

    /**
     * Update existing chore
     */
    fun updateChore(chore: Chore): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Update local database first (optimistic update)
            choreDao.updateChore(chore.toEntity())

            // Sync with backend
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
            
            if (response.isSuccessful && response.body()?.success == true) {
                val updatedChore = response.body()?.chore
                if (updatedChore != null) {
                    // Update local database with server response
                    choreDao.updateChore(updatedChore.toEntity())
                    Log.d(TAG, "Chore updated successfully on backend: ${updatedChore.id}")
                    emit(Result.Success(updatedChore))
                } else {
                    Log.w(TAG, "Backend updated chore but returned null chore")
                    emit(Result.Success(chore))
                }
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to update chore on backend"
                Log.e(TAG, "Failed to update chore on backend: $errorMsg")
                // Keep local data even if backend sync fails
                emit(Result.Success(chore))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating chore", e)
            emit(Result.Error(e.message ?: "Failed to update chore"))
        }
    }

    /**
     * Delete chore
     */
    fun deleteChore(chore: Chore): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Delete from local database first (optimistic update)
            choreDao.deleteChore(chore.toEntity())

            // Sync with backend
            val request = DeleteChoreRequest(
                userId = session.userId,
                choreId = chore.id
            )

            val response = api.deleteChore(request = request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Chore deleted successfully on backend: ${chore.id}")
                emit(Result.Success(Unit))
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to delete chore on backend"
                Log.e(TAG, "Failed to delete chore on backend: $errorMsg")
                // Chore already deleted locally, emit success anyway
                emit(Result.Success(Unit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting chore", e)
            emit(Result.Error(e.message ?: "Failed to delete chore"))
        }
    }

    /**
     * Complete chore
     */
    fun completeChore(choreId: String, userId: String, photoProof: String? = null): Flow<Result<Chore>> = flow {
        emit(Result.Loading)
        try {
            val chore = choreDao.getChoreById(choreId)?.toDomain()
            if (chore == null) {
                emit(Result.Error("Chore not found"))
                return@flow
            }

            // Update local database first (optimistic update)
            val updatedChore = chore.copy(
                status = com.chorequest.domain.models.ChoreStatus.COMPLETED,
                completedBy = userId,
                completedAt = java.time.Instant.now().toString(),
                photoProof = photoProof
            )
            choreDao.updateChore(updatedChore.toEntity())

            // Sync with backend
            val request = CompleteChoreRequest(
                userId = userId,
                choreId = choreId,
                photoProof = photoProof
            )

            val response = api.completeChore(request = request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val completedChore = response.body()?.chore
                if (completedChore != null) {
                    // Update local database with server response
                    choreDao.updateChore(completedChore.toEntity())
                    Log.d(TAG, "Chore completed successfully on backend: $choreId")
                    emit(Result.Success(completedChore))
                } else {
                    Log.w(TAG, "Backend completed chore but returned null chore")
                    emit(Result.Success(updatedChore))
                }
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to complete chore on backend"
                Log.e(TAG, "Failed to complete chore on backend: $errorMsg")
                // Keep local data even if backend sync fails
                emit(Result.Success(updatedChore))
            }
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
