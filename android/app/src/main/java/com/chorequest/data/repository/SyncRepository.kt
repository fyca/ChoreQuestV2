package com.chorequest.data.repository

import android.util.Log
import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.ActivityLogDao
import com.chorequest.data.local.dao.ChoreDao
import com.chorequest.data.local.dao.RewardDao
import com.chorequest.data.local.dao.RewardRedemptionDao
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
    private val rewardRedemptionDao: RewardRedemptionDao,
    private val userDao: UserDao,
    private val activityLogDao: ActivityLogDao,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val gson: Gson,
    private val driveApiService: com.chorequest.data.drive.DriveApiService,
    private val tokenManager: com.chorequest.data.drive.TokenManager
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

            Log.i(TAG, "Starting background sync for family ${session.familyId}")
            val familyId = session.familyId

            // Perform background sync of all data types
            // Note: Screens still load data on-demand for immediate UI updates,
            // but background sync keeps local database fresh for offline access
            syncChores(familyId)
            syncRewards(familyId)
            syncUsers(familyId)
            syncActivityLogs(familyId)

            Log.i(TAG, "Background sync completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            false
        }
    }

    /**
     * Syncs chores from the server to local database
     * Uses direct Google Drive API first, falls back to Apps Script if needed
     * IMPORTANT: Only deletes local data AFTER successfully fetching from Drive
     */
    private suspend fun syncChores(familyId: String) {
        try {
            val session = authRepository.getCurrentSession()
            if (session == null) {
                Log.e(TAG, "No session for syncing chores")
                return
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            val folderId = session.driveWorkbookLink
            
            if (accessToken != null && folderId.isNotBlank()) {
                try {
                    Log.d(TAG, "Syncing chores using direct Drive API...")
                    val choresData = readChoresFromDrive(accessToken, folderId)
                    
                    if (choresData != null) {
                        val chores = choresData.chores ?: emptyList()
                        
                        // IMPORTANT: Only delete local data AFTER successfully fetching and parsing from Drive
                        choreDao.deleteAllChores()
                        if (chores.isNotEmpty()) {
                            choreDao.insertChores(chores.map { it.toEntity() })
                        }
                        Log.d(TAG, "Synced ${chores.size} chores via Drive API")
                        return
                    } else {
                        Log.w(TAG, "Failed to read chores from Drive, falling back to Apps Script")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            } else {
                Log.d(TAG, "No access token or folder ID available, using Apps Script")
            }

            // Fallback to Apps Script
            Log.d(TAG, "Syncing chores using Apps Script...")
            val response = api.getData(
                path = "data",
                action = "get",
                type = "chores",
                familyId = familyId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                
                if (data != null) {
                    // Convert data to JSON string and parse as ChoresData
                    val jsonString = gson.toJson(data)
                    val choresData = gson.fromJson(jsonString, ChoresData::class.java)
                    
                    val chores = choresData.chores ?: emptyList()
                    
                    // IMPORTANT: Only delete local data AFTER successfully fetching and parsing from Drive
                    choreDao.deleteAllChores()
                    if (chores.isNotEmpty()) {
                        choreDao.insertChores(chores.map { it.toEntity() })
                    }
                    Log.d(TAG, "Synced ${chores.size} chores via Apps Script")
                } else {
                    Log.w(TAG, "No chores data in response body - keeping existing local data")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to sync chores via Apps Script: $errorBody - keeping existing local data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing chores", e)
            // Don't throw - allow other syncs to continue
        }
    }

    /**
     * Syncs rewards from the server to local database
     * Uses direct Google Drive API first, falls back to Apps Script if needed
     * IMPORTANT: Only deletes local data AFTER successfully fetching from Drive
     */
    private suspend fun syncRewards(familyId: String) {
        try {
            val session = authRepository.getCurrentSession()
            if (session == null) {
                Log.e(TAG, "No session for syncing rewards")
                return
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            val folderId = session.driveWorkbookLink
            
            if (accessToken != null && folderId.isNotBlank()) {
                try {
                    Log.d(TAG, "Syncing rewards using direct Drive API...")
                    val rewardsData = readRewardsFromDrive(accessToken, folderId)
                    
                    if (rewardsData != null) {
                        val rewards = rewardsData.rewards ?: emptyList()
                        
                        // IMPORTANT: Only delete local data AFTER successfully fetching and parsing from Drive
                        rewardDao.deleteAllRewards()
                        if (rewards.isNotEmpty()) {
                            rewardDao.insertRewards(rewards.map { it.toEntity() })
                        }
                        Log.d(TAG, "Synced ${rewards.size} rewards via Drive API")
                        return
                    } else {
                        Log.w(TAG, "Failed to read rewards from Drive, falling back to Apps Script")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            } else {
                Log.d(TAG, "No access token or folder ID available, using Apps Script")
            }

            // Fallback to Apps Script
            Log.d(TAG, "Syncing rewards using Apps Script...")
            val response = api.getData(
                path = "data",
                action = "get",
                type = "rewards",
                familyId = familyId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                
                if (data != null) {
                    val jsonString = gson.toJson(data)
                    val rewardsData = gson.fromJson(jsonString, RewardsData::class.java)
                    
                    val rewards = rewardsData.rewards ?: emptyList()
                    
                    // IMPORTANT: Only delete local data AFTER successfully fetching and parsing from Drive
                    rewardDao.deleteAllRewards()
                    if (rewards.isNotEmpty()) {
                        rewardDao.insertRewards(rewards.map { it.toEntity() })
                    }
                    Log.d(TAG, "Synced ${rewards.size} rewards via Apps Script")
                } else {
                    Log.w(TAG, "No rewards data in response body - keeping existing local data")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to sync rewards via Apps Script: $errorBody - keeping existing local data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing rewards", e)
            // Don't throw - allow other syncs to continue
        }
    }

    // Batch sync removed - data now loads on-demand when screens open

    /**
     * Syncs users from the server to local database
     * Uses direct Google Drive API first, falls back to Apps Script if needed
     */
    private suspend fun syncUsers(familyId: String) {
        try {
            val session = authRepository.getCurrentSession()
            if (session == null) {
                Log.e(TAG, "No session for syncing users")
                return
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            val folderId = session.driveWorkbookLink
            
            if (accessToken != null && folderId.isNotBlank()) {
                try {
                    Log.d(TAG, "Syncing users using direct Drive API...")
                    val usersData = readUsersFromDrive(accessToken, folderId)
                    
                    if (usersData != null && usersData.users != null) {
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
                        Log.d(TAG, "Synced ${users.size} users via Drive API")
                        return
                    } else {
                        Log.w(TAG, "Failed to read users from Drive, falling back to Apps Script")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            } else {
                Log.d(TAG, "No access token or folder ID available, using Apps Script")
            }

            // Fallback to Apps Script
            Log.d(TAG, "Syncing users using Apps Script...")
            val response = api.listUsers(familyId = familyId)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val users = response.body()?.users ?: emptyList()
                
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
                Log.d(TAG, "Synced ${users.size} users via Apps Script")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to sync users via Apps Script: $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing users", e)
            // Don't throw - allow other syncs to continue
        }
    }

    /**
     * Syncs activity logs from the server to local database
     * Uses direct Google Drive API first, falls back to Apps Script if needed
     */
    private suspend fun syncActivityLogs(familyId: String) {
        try {
            val session = authRepository.getCurrentSession()
            if (session == null) {
                Log.e(TAG, "No session for syncing activity logs")
                return
            }

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            val folderId = session.driveWorkbookLink
            
            if (accessToken != null && folderId.isNotBlank()) {
                try {
                    Log.d(TAG, "Syncing activity logs using direct Drive API...")
                    val activityLogData = readActivityLogsFromDrive(accessToken, folderId)
                    
                    if (activityLogData != null && activityLogData.logs != null) {
                        val logs = activityLogData.logs
                        
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
                            Log.d(TAG, "Synced ${validEntities.size} activity logs via Drive API (${logs.size - validEntities.size} skipped)")
                        } else {
                            Log.d(TAG, "No valid activity logs to sync")
                        }
                        return
                    } else {
                        Log.w(TAG, "Failed to read activity logs from Drive, falling back to Apps Script")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            } else {
                Log.d(TAG, "No access token or folder ID available, using Apps Script")
            }

            // Fallback to Apps Script
            Log.d(TAG, "Syncing activity logs using Apps Script...")
            val response = api.getActivityLogs(
                familyId = familyId,
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
                        Log.d(TAG, "Synced ${validEntities.size} activity logs via Apps Script (${logs.size - validEntities.size} skipped)")
                    } else {
                        Log.w(TAG, "No valid activity logs to sync")
                    }
                } else {
                    Log.d(TAG, "No activity logs in response")
                }
            } else {
                Log.e(TAG, "Failed to sync activity logs via Apps Script: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing activity logs", e)
            // Don't throw - allow other syncs to continue
        }
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
     * Helper: Read rewards from Drive using direct API
     */
    private suspend fun readRewardsFromDrive(accessToken: String, folderId: String): RewardsData? {
        return try {
            val fileName = "rewards.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, RewardsData::class.java)
            } else {
                // File doesn't exist, return empty
                RewardsData(rewards = emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading rewards from Drive", e)
            null
        }
    }

    /**
     * Helper: Read users from Drive using direct API
     */
    private suspend fun readUsersFromDrive(accessToken: String, folderId: String): UsersData? {
        return try {
            val fileName = "users.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, UsersData::class.java)
            } else {
                // File doesn't exist, return null
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading users from Drive", e)
            null
        }
    }

    /**
     * Helper: Read activity logs from Drive using direct API
     */
    private suspend fun readActivityLogsFromDrive(accessToken: String, folderId: String): ActivityLogData? {
        return try {
            val fileName = "activity_log.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, ActivityLogData::class.java)
            } else {
                // File doesn't exist, return empty
                ActivityLogData(logs = emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading activity logs from Drive", e)
            null
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
