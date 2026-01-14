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

            // Call backend to delete all data
            val response = api.deleteAllData(
                request = com.chorequest.data.remote.DeleteAllDataRequest(
                    userId = session.userId,
                    familyId = session.familyId
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all data", e)
            emit(Result.Error("Error deleting all data: ${e.message}"))
        }
    }
}
