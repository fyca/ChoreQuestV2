package com.chorequest.data.repository

import android.util.Log
import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.ActivityLogDao
import com.chorequest.data.local.dao.ChoreDao
import com.chorequest.data.local.dao.RewardDao
import com.chorequest.data.local.dao.UserDao
import com.chorequest.data.local.entities.toDomain
import com.chorequest.data.local.entities.toEntity
import com.chorequest.data.remote.ChoreQuestApi
import com.chorequest.domain.models.*
import com.chorequest.utils.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val choreDao: ChoreDao,
    private val rewardDao: RewardDao,
    private val userDao: UserDao,
    private val activityLogDao: ActivityLogDao,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "SyncRepository"
        private const val SYNC_INTERVAL_MINUTES = 15L
        private const val SYNC_INTERVAL_MS = SYNC_INTERVAL_MINUTES * 60 * 1000
    }

    /**
     * Performs a full sync of all data from the server
     * @param forceSync If true, syncs regardless of last sync timestamp. If false, only syncs if more than SYNC_INTERVAL_MINUTES have passed.
     * @return true if sync was successful or skipped, false if sync failed
     */
    suspend fun syncAll(forceSync: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val session = authRepository.getCurrentSession()
            if (session == null) {
                Log.e(TAG, "No active session, cannot sync")
                return@withContext false
            }

            // Check if sync is needed based on timestamp (unless forced)
            if (!forceSync) {
                val lastSynced = session.lastSynced
                if (lastSynced != null) {
                    val timeSinceLastSync = System.currentTimeMillis() - lastSynced
                    if (timeSinceLastSync < SYNC_INTERVAL_MS) {
                        val minutesSinceSync = timeSinceLastSync / (60 * 1000)
                        Log.d(TAG, "Skipping sync - only ${minutesSinceSync} minutes since last sync (need ${SYNC_INTERVAL_MINUTES} minutes)")
                        return@withContext true // Return true since skipping is not an error
                    }
                }
            }

            Log.i(TAG, "Starting sync (forceSync=$forceSync)")
            
            // Sync chores
            syncChores(session.familyId)
            
            // Sync rewards
            syncRewards(session.familyId)
            
            // Sync users
            syncUsers(session.familyId)
            
            // Sync activity logs
            syncActivityLogs(session.familyId)

            // Update timestamp only if sync actually ran
            updateLastSyncTime(System.currentTimeMillis())
            
            Log.i(TAG, "Full sync completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            false
        }
    }

    /**
     * Syncs chores from the server to local database
     */
    private suspend fun syncChores(familyId: String) {
        try {
            Log.d(TAG, "Syncing chores from backend...")
            val response = api.getData(
                path = "data",
                action = "get",
                type = "chores"
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                
                if (data != null) {
                    // Convert data to JSON string and parse as ChoresData
                    val jsonString = gson.toJson(data)
                    val choresData = gson.fromJson(jsonString, ChoresData::class.java)
                    
                    val chores = choresData.chores ?: emptyList()
                    
                    // Update local database
                    choreDao.deleteAllChores()
                    choreDao.insertChores(chores.map { it.toEntity() })
                    Log.d(TAG, "Synced ${chores.size} chores")
                } else {
                    Log.d(TAG, "No chores data in response")
                }
            } else {
                Log.e(TAG, "Failed to sync chores: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing chores", e)
            // Don't throw - allow other syncs to continue
        }
    }

    /**
     * Syncs rewards from the server to local database
     */
    private suspend fun syncRewards(familyId: String) {
        try {
            Log.d(TAG, "Syncing rewards from backend...")
            val response = api.getData(
                path = "data",
                action = "get",
                type = "rewards"
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                
                if (data != null) {
                    val jsonString = gson.toJson(data)
                    val rewardsData = gson.fromJson(jsonString, RewardsData::class.java)
                    
                    val rewards = rewardsData.rewards ?: emptyList()
                    
                    rewardDao.deleteAllRewards()
                    rewardDao.insertRewards(rewards.map { it.toEntity() })
                    Log.d(TAG, "Synced ${rewards.size} rewards")
                } else {
                    Log.d(TAG, "No rewards data in response")
                }
            } else {
                Log.e(TAG, "Failed to sync rewards: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing rewards", e)
            // Don't throw - allow other syncs to continue
        }
    }

    /**
     * Syncs users from the server to local database
     */
    private suspend fun syncUsers(familyId: String) {
        try {
            Log.d(TAG, "Syncing users from backend...")
            val response = api.getData(
                path = "data",
                action = "get",
                type = "users"
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                
                if (data != null) {
                    val jsonString = gson.toJson(data)
                    val usersData = gson.fromJson(jsonString, UsersData::class.java)
                    
                    val users = usersData.users
                    
                    // Don't delete all users - keep the current logged-in user
                    // Just update/insert the synced users
                    users.forEach { user ->
                        val existing = userDao.getUserById(user.id)
                        if (existing != null) {
                            userDao.updateUser(user.toEntity())
                        } else {
                            userDao.insertUser(user.toEntity())
                        }
                    }
                    Log.d(TAG, "Synced ${users.size} users")
                } else {
                    Log.d(TAG, "No users data in response")
                }
            } else {
                Log.e(TAG, "Failed to sync users: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing users", e)
            // Don't throw - allow other syncs to continue
        }
    }

    /**
     * Syncs activity logs from the server to local database
     */
    private suspend fun syncActivityLogs(familyId: String) {
        try {
            Log.d(TAG, "Syncing activity logs from backend...")
            val response = api.getActivityLogs(
                userId = null,
                actionType = null,
                startDate = null,
                endDate = null,
                page = 1,
                pageSize = 100 // Sync up to 100 most recent logs
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val logs = response.body()?.logs
                
                if (logs != null && logs.isNotEmpty()) {
                    // Filter out invalid logs and convert to entities
                    val validEntities = logs.mapNotNull { log ->
                        try {
                            // Validate required fields before conversion
                            if (log.actionType == null) {
                                Log.w(TAG, "Skipping activity log ${log.id} - missing actionType")
                                return@mapNotNull null
                            }
                            log.toEntity()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting activity log ${log.id} to entity: ${e.message}", e)
                            null
                        }
                    }
                    
                    if (validEntities.isNotEmpty()) {
                        activityLogDao.insertLogs(validEntities)
                        Log.d(TAG, "Synced ${validEntities.size} activity logs (${logs.size - validEntities.size} skipped)")
                    } else {
                        Log.w(TAG, "No valid activity logs to sync")
                    }
                } else {
                    Log.d(TAG, "No activity logs in response")
                }
            } else {
                Log.e(TAG, "Failed to sync activity logs: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing activity logs", e)
            // Don't throw - allow other syncs to continue
        }
    }

    /**
     * Gets the last sync timestamp
     */
    suspend fun getLastSyncTime(): Long? = withContext(Dispatchers.IO) {
        authRepository.getCurrentSession()?.lastSynced
    }

    /**
     * Updates the last sync timestamp
     */
    suspend fun updateLastSyncTime(timestamp: Long) = withContext(Dispatchers.IO) {
        val session = authRepository.getCurrentSession()
        if (session != null) {
            // Update session with new timestamp and persist it
            sessionManager.updateSession { it.copy(lastSynced = timestamp) }
            Log.d(TAG, "Updated last sync timestamp to: $timestamp")
        }
    }
}
