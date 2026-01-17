package com.chorequest.data.repository

import android.util.Log
import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.*
import com.chorequest.data.remote.ChoreQuestApi
import com.chorequest.utils.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for data management operations (delete all, etc.)
 */
@Singleton
class DataRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val sessionManager: SessionManager,
    private val choreDao: ChoreDao,
    private val rewardDao: RewardDao,
    private val userDao: UserDao,
    private val activityLogDao: ActivityLogDao,
    private val transactionDao: TransactionDao
) {
    companion object {
        private const val TAG = "DataRepository"
    }

    /**
     * Delete all user data (only accessible by primary parent)
     * This deletes all data except the primary parent user
     */
    suspend fun deleteAllData(): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            Log.d(TAG, "Starting delete all data operation for family: ${session.familyId}")
            
            // Call backend to delete all data
            // Use withContext to ensure the operation completes even if the flow is cancelled
            val response = withContext(Dispatchers.IO) {
                try {
                    api.deleteAllData(
                        request = com.chorequest.data.remote.DeleteAllDataRequest(
                            userId = session.userId,
                            familyId = session.familyId
                        )
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Delete operation was cancelled", e)
                    throw e // Re-throw cancellation to allow proper handling
                } catch (e: Exception) {
                    Log.e(TAG, "Error calling delete API", e)
                    null
                }
            }

            if (response == null) {
                emit(Result.Error("Failed to connect to server. Please try again."))
                return@flow
            }

            if (response.isSuccessful && response.body()?.success == true) {
                Log.i(TAG, "Backend deletion successful, clearing local database")
                // Clear local database
                // Backend preserves the primary parent, so we clear everything locally
                // User will need to sync or re-login to restore their account
                withContext(Dispatchers.IO) {
                    choreDao.deleteAllChores()
                    rewardDao.deleteAllRewards()
                    activityLogDao.deleteAllLogs()
                    transactionDao.deleteAllTransactions()
                    userDao.deleteAllUsers()
                }
                Log.i(TAG, "All data deleted successfully")
                emit(Result.Success(Unit))
            } else {
                val errorMsg = response.body()?.error ?: "Failed to delete all data"
                Log.e(TAG, "Failed to delete all data: $errorMsg")
                emit(Result.Error(errorMsg))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.e(TAG, "Delete all data operation was cancelled", e)
            emit(Result.Error("Operation was cancelled. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all data", e)
            emit(Result.Error("Error deleting all data: ${e.message}"))
        }
    }
}
