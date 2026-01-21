package com.chorequest.data.repository

import android.content.Context
import android.util.Log
import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.RewardDao
import com.chorequest.data.local.dao.RewardRedemptionDao
import com.chorequest.data.local.entities.toEntity
import com.chorequest.data.local.entities.toDomain
import com.chorequest.data.remote.*
import com.chorequest.domain.models.Reward
import com.chorequest.domain.models.RewardRedemption
import com.chorequest.domain.models.RewardRedemptionsData
import com.chorequest.utils.Result
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing reward data
 */
@Singleton
class RewardRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val rewardDao: RewardDao,
    private val rewardRedemptionDao: RewardRedemptionDao,
    private val sessionManager: SessionManager,
    private val gson: Gson,
    private val driveApiService: com.chorequest.data.drive.DriveApiService,
    private val tokenManager: com.chorequest.data.drive.TokenManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RewardRepository"
    }

    /**
     * Get all rewards from local database
     */
    fun getAllRewards(): Flow<List<Reward>> {
        return rewardDao.getAllRewards().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get reward by ID from local database
     */
    suspend fun getRewardById(rewardId: String): Reward? {
        return rewardDao.getRewardById(rewardId)?.toDomain()
    }

    /**
     * Create a new reward
     * Drive is the source of truth - save to Drive FIRST, then cache locally
     * Uses direct Drive API first, falls back to Apps Script if needed
     */
    fun createReward(reward: Reward): Flow<Result<Reward>> = flow {
        emit(Result.Loading)
        val session = sessionManager.loadSession()
        if (session == null) {
            emit(Result.Error("No active session"))
            return@flow
        }

        // Try direct Drive API first
        val accessToken = tokenManager.getValidAccessToken()
        if (accessToken != null) {
            try {
                Log.d(TAG, "Using direct Drive API to create reward")
                val folderId = session.driveWorkbookLink
                
                // Read current rewards
                val rewardsData = readRewardsFromDrive(accessToken, folderId) ?: com.chorequest.domain.models.RewardsData(rewards = emptyList())
                
                // Add new reward
                val updatedRewards = rewardsData.rewards + reward
                val updatedData = rewardsData.copy(rewards = updatedRewards)
                
                // Write back to Drive
                if (writeRewardsToDrive(accessToken, folderId, updatedData)) {
                    // Update local cache
                    rewardDao.insertReward(reward.toEntity())
                    Log.d(TAG, "Reward created successfully via Drive API: ${reward.id}")
                    emit(Result.Success(reward))
                    return@flow
                } else {
                    Log.w(TAG, "Failed to write reward to Drive, falling back to Apps Script")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Re-throw cancellation exceptions
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
            }
        } else {
            Log.d(TAG, "No access token available, using Apps Script")
        }

        // Fallback to Apps Script
        val response = api.createReward(
            request = CreateRewardRequest(
                creatorId = session.userId,
                title = reward.title,
                description = reward.description,
                pointCost = reward.pointCost,
                imageUrl = reward.imageUrl,
                available = reward.available,
                quantity = reward.quantity
            )
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val created = response.body()?.reward
            if (created != null) {
                // Only save to local cache AFTER successful Drive save
                rewardDao.insertReward(created.toEntity())
                Log.d(TAG, "Reward created successfully via Apps Script: ${created.id}")
                emit(Result.Success(created))
            } else {
                Log.w(TAG, "Apps Script created reward but returned null reward")
                emit(Result.Error("Reward created but response was invalid"))
            }
        } else {
            val errorMsg = response.body()?.error ?: response.message() ?: "Failed to create reward"
            Log.e(TAG, "Failed to create reward via Apps Script: $errorMsg")
            emit(Result.Error("Failed to save reward: $errorMsg"))
        }
    }.catch { e ->
        if (e is kotlinx.coroutines.CancellationException) {
            throw e
        }
        Log.e(TAG, "Create reward failed", e)
        emit(Result.Error(e.message ?: "Failed to create reward"))
    }

    /**
     * Update an existing reward
     * Drive is the source of truth - save to Drive FIRST, then cache locally
     * Uses direct Drive API first, falls back to Apps Script if needed
     */
    fun updateReward(reward: Reward): Flow<Result<Reward>> = flow {
        emit(Result.Loading)
        val session = sessionManager.loadSession()
        if (session == null) {
            emit(Result.Error("No active session"))
            return@flow
        }

        // Try direct Drive API first
        val accessToken = tokenManager.getValidAccessToken()
        if (accessToken != null) {
            try {
                Log.d(TAG, "Using direct Drive API to update reward")
                val folderId = session.driveWorkbookLink
                
                // Read current rewards
                val rewardsData = readRewardsFromDrive(accessToken, folderId)
                if (rewardsData != null) {
                    // Find and update the reward
                    val updatedRewards = rewardsData.rewards.map { 
                        if (it.id == reward.id) reward else it
                    }
                    val updatedData = rewardsData.copy(rewards = updatedRewards)
                    
                    // Write back to Drive
                    if (writeRewardsToDrive(accessToken, folderId, updatedData)) {
                        // Update local cache
                        rewardDao.updateReward(reward.toEntity())
                        Log.d(TAG, "Reward updated successfully via Drive API: ${reward.id}")
                        emit(Result.Success(reward))
                        return@flow
                    } else {
                        Log.w(TAG, "Failed to write reward update to Drive, falling back to Apps Script")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Re-throw cancellation exceptions
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
            }
        } else {
            Log.d(TAG, "No access token available, using Apps Script")
        }

        // Fallback to Apps Script
        val response = api.updateReward(
            request = UpdateRewardRequest(
                userId = session.userId,
                rewardId = reward.id,
                updates = RewardUpdates(
                    title = reward.title,
                    description = reward.description,
                    pointCost = reward.pointCost,
                    imageUrl = reward.imageUrl,
                    available = reward.available,
                    quantity = reward.quantity
                )
            )
        )

        if (response.isSuccessful && response.body()?.success == true) {
            val updated = response.body()?.reward
            if (updated != null) {
                // Only update local cache AFTER successful Drive save
                rewardDao.updateReward(updated.toEntity())
                Log.d(TAG, "Reward updated successfully via Apps Script: ${updated.id}")
                emit(Result.Success(updated))
            } else {
                Log.w(TAG, "Apps Script updated reward but returned null reward")
                emit(Result.Error("Reward updated but response was invalid"))
            }
        } else {
            val errorMsg = response.body()?.error ?: response.message() ?: "Failed to update reward"
            Log.e(TAG, "Failed to update reward via Apps Script: $errorMsg")
            emit(Result.Error("Failed to save reward update: $errorMsg"))
        }
    }.catch { e ->
        if (e is kotlinx.coroutines.CancellationException) {
            throw e
        }
        Log.e(TAG, "Update reward failed", e)
        emit(Result.Error(e.message ?: "Failed to update reward"))
    }

    /**
     * Delete a reward
     * Drive is the source of truth - delete from Drive FIRST, then remove from local cache
     * Uses direct Drive API first, falls back to Apps Script if needed
     */
    fun deleteReward(reward: Reward): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        val session = sessionManager.loadSession()
        if (session == null) {
            emit(Result.Error("No active session"))
            return@flow
        }

        // Try direct Drive API first
        val accessToken = tokenManager.getValidAccessToken()
        if (accessToken != null) {
            try {
                Log.d(TAG, "Using direct Drive API to delete reward")
                val folderId = session.driveWorkbookLink
                
                // Read current rewards
                val rewardsData = readRewardsFromDrive(accessToken, folderId)
                if (rewardsData != null) {
                    // Remove the reward
                    val updatedRewards = rewardsData.rewards.filter { it.id != reward.id }
                    val updatedData = rewardsData.copy(rewards = updatedRewards)
                    
                    // Write back to Drive
                    if (writeRewardsToDrive(accessToken, folderId, updatedData)) {
                        // Remove from local cache
                        rewardDao.deleteReward(reward.toEntity())
                        Log.d(TAG, "Reward deleted successfully via Drive API: ${reward.id}")
                        emit(Result.Success(Unit))
                        return@flow
                    } else {
                        Log.w(TAG, "Failed to delete reward from Drive, falling back to Apps Script")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Re-throw cancellation exceptions
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
            }
        } else {
            Log.d(TAG, "No access token available, using Apps Script")
        }

        // Fallback to Apps Script
        val response = api.deleteReward(
            request = DeleteRewardRequest(
                userId = session.userId,
                rewardId = reward.id
            )
        )
        if (response.isSuccessful && response.body()?.success == true) {
            // Only remove from local cache AFTER successful Drive deletion
            rewardDao.deleteReward(reward.toEntity())
            Log.d(TAG, "Reward deleted successfully via Apps Script: ${reward.id}")
            emit(Result.Success(Unit))
        } else {
            val errorMsg = response.body()?.error ?: response.message() ?: "Failed to delete reward"
            Log.e(TAG, "Failed to delete reward via Apps Script: $errorMsg")
            emit(Result.Error("Failed to delete reward: $errorMsg"))
        }
    }.catch { e ->
        if (e is kotlinx.coroutines.CancellationException) {
            throw e
        }
        Log.e(TAG, "Delete reward failed", e)
        emit(Result.Error(e.message ?: "Failed to delete reward"))
    }

    /**
     * Redeem a reward
     */
    fun redeemReward(rewardId: String, userId: String): Flow<Result<String>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session != null) {
                val response = api.redeemReward(
                    request = RedeemRewardRequest(
                        userId = userId,
                        rewardId = rewardId
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    // If server returned updated reward, cache it
                    response.body()?.reward?.let { rewardDao.updateReward(it.toEntity()) }
                    emit(Result.Success(response.body()?.message ?: "Reward redeemed successfully!"))
                } else {
                    val errorMessage = response.body()?.error ?: response.body()?.message ?: "Failed to redeem reward"
                    emit(Result.Error(errorMessage))
                }
            } else {
                emit(Result.Error("No active session"))
            }
        } catch (e: Exception) {
            Log.e("RewardRepository", "Redeem reward failed: ${e.message}")
            emit(Result.Error("Failed to redeem reward: ${e.message}"))
        }
    }

    /**
     * Get reward redemptions from local database as Flow
     */
    fun getLocalRedemptions(userId: String? = null): Flow<List<RewardRedemption>> {
        return if (userId != null) {
            rewardRedemptionDao.getRedemptionsByUserId(userId).map { entities ->
                entities.map { it.toDomain() }
            }
        } else {
            rewardRedemptionDao.getAllRedemptions().map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    /**
     * Helper: Read rewards from Drive using direct API
     */
    private suspend fun readRewardsFromDrive(accessToken: String, folderId: String): com.chorequest.domain.models.RewardsData? {
        return try {
            val fileName = "rewards.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, com.chorequest.domain.models.RewardsData::class.java)
            } else {
                // File doesn't exist, return empty
                com.chorequest.domain.models.RewardsData(rewards = emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading rewards from Drive", e)
            null
        }
    }

    /**
     * Helper: Write rewards to Drive using direct API
     */
    private suspend fun writeRewardsToDrive(accessToken: String, folderId: String, rewardsData: com.chorequest.domain.models.RewardsData): Boolean {
        return try {
            val fileName = "rewards.json"
            val jsonContent = gson.toJson(rewardsData)
            driveApiService.writeFileContent(accessToken, folderId, fileName, jsonContent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing rewards to Drive", e)
            false
        }
    }

    /**
     * Helper: Read reward redemptions from Drive using direct API
     */
    private suspend fun readRewardRedemptionsFromDrive(accessToken: String, folderId: String): RewardRedemptionsData? {
        return try {
            val fileName = "reward_redemptions.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, RewardRedemptionsData::class.java)
            } else {
                // File doesn't exist, return empty
                RewardRedemptionsData(redemptions = emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading reward redemptions from Drive", e)
            null
        }
    }

    /**
     * Get reward redemptions for the current user, or all pending redemptions for the family
     * Uses direct Drive API for faster performance (no cold start)
     */
    suspend fun getRewardRedemptions(userId: String? = null, familyId: String? = null): Result<List<RewardRedemption>> {
        // First, get local data immediately
        val localRedemptions = try {
            if (userId != null) {
                rewardRedemptionDao.getRedemptionsByUserId(userId)
            } else {
                rewardRedemptionDao.getAllRedemptions()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading local redemptions: ${e.message}")
            null
        }
        
        // Get first value from Flow (local data)
        val localList = localRedemptions?.let { flow ->
            withContext(Dispatchers.IO) {
                flow.firstOrNull()?.map { it.toDomain() } ?: emptyList()
            }
        } ?: emptyList()
        
        // Try direct Drive API first
        val session = sessionManager.loadSession()
        val accessToken = tokenManager.getValidAccessToken()
        
        if (session != null && accessToken != null) {
            try {
                Log.d(TAG, "Loading reward redemptions from Drive on-demand")
                val folderId = session.driveWorkbookLink
                val redemptionsData = readRewardRedemptionsFromDrive(accessToken, folderId)
                
                if (redemptionsData != null) {
                    var redemptions = redemptionsData.redemptions
                    
                    // Filter by userId if provided
                    if (userId != null) {
                        redemptions = redemptions.filter { it.userId == userId }
                    }
                    
                    // Update local database
                    rewardRedemptionDao.deleteAllRedemptions()
                    if (redemptions.isNotEmpty()) {
                        rewardRedemptionDao.insertRedemptions(redemptions.map { it.toEntity() })
                    }
                    Log.d(TAG, "Loaded ${redemptions.size} reward redemptions from Drive")
                    return Result.Success(redemptions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading reward redemptions from Drive, falling back to Apps Script", e)
            }
        } else {
            Log.d(TAG, "No access token, falling back to Apps Script")
        }
        
        // Fallback to Apps Script
        kotlinx.coroutines.coroutineScope {
            launch(Dispatchers.IO) {
                try {
                    val response = api.getRewardRedemptions(userId = userId, familyId = familyId)
                    if (response.isSuccessful && response.body()?.success == true) {
                        val redemptions = response.body()?.redemptions ?: emptyList()
                        // Update local database
                        rewardRedemptionDao.deleteAllRedemptions()
                        if (redemptions.isNotEmpty()) {
                            rewardRedemptionDao.insertRedemptions(redemptions.map { it.toEntity() })
                        }
                        Log.d(TAG, "Synced ${redemptions.size} redemptions from Apps Script")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background sync of redemptions failed: ${e.message}")
                }
            }
        }
        
        // Return local data immediately
        return Result.Success(localList)
    }
    
    /**
     * Approve a reward redemption
     */
    fun approveRewardRedemption(parentId: String, redemptionId: String): Flow<Result<String>> = flow {
        emit(Result.Loading)
        try {
            val response = api.approveRewardRedemption(
                request = ApproveRewardRedemptionRequest(
                    parentId = parentId,
                    redemptionId = redemptionId
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()?.message ?: "Reward approved successfully!"))
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to approve reward"
                emit(Result.Error(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("RewardRepository", "Approve redemption failed: ${e.message}")
            emit(Result.Error("Failed to approve reward: ${e.message}"))
        }
    }
    
    /**
     * Deny a reward redemption
     */
    fun denyRewardRedemption(parentId: String, redemptionId: String): Flow<Result<String>> = flow {
        emit(Result.Loading)
        try {
            val response = api.denyRewardRedemption(
                request = DenyRewardRedemptionRequest(
                    parentId = parentId,
                    redemptionId = redemptionId
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                emit(Result.Success(response.body()?.message ?: "Reward denied."))
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to deny reward"
                emit(Result.Error(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("RewardRepository", "Deny redemption failed: ${e.message}")
            emit(Result.Error("Failed to deny reward: ${e.message}"))
        }
    }

    /**
     * Fetch rewards from server and update local database
     * Uses direct Drive API first, falls back to Apps Script if needed
     */
    suspend fun syncRewards() {
        try {
            val session = sessionManager.loadSession()
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
            val response = api.listRewards(familyId = session.familyId)
            if (response.isSuccessful && response.body()?.success == true) {
                val rewards = response.body()?.rewards ?: emptyList()
                
                // IMPORTANT: Only delete local data AFTER successfully fetching from Apps Script
                rewardDao.deleteAllRewards()
                if (rewards.isNotEmpty()) {
                    rewardDao.insertRewards(rewards.map { it.toEntity() })
                }
                Log.d(TAG, "Synced ${rewards.size} rewards via Apps Script")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to sync rewards via Apps Script: $errorBody - keeping existing local data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync rewards failed: ${e.message}", e)
            // Don't throw - allow other operations to continue
        }
    }
}
