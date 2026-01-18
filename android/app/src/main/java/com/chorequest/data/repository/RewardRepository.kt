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
import com.chorequest.utils.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
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
    @ApplicationContext private val context: Context
) {

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
     */
    fun createReward(reward: Reward): Flow<Result<Reward>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Save to Drive FIRST (Drive is source of truth)
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
                    Log.d("RewardRepository", "Reward created successfully on Drive: ${created.id}")
                    emit(Result.Success(created))
                } else {
                    Log.w("RewardRepository", "Drive created reward but returned null reward")
                    emit(Result.Error("Reward created but response was invalid"))
                }
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to create reward on Drive"
                Log.e("RewardRepository", "Failed to create reward on Drive: $errorMsg")
                emit(Result.Error("Failed to save reward to Drive: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e("RewardRepository", "Create reward failed: ${e.message}")
            emit(Result.Error(e.message ?: "Failed to create reward"))
        }
    }

    /**
     * Update an existing reward
     * Drive is the source of truth - save to Drive FIRST, then cache locally
     */
    fun updateReward(reward: Reward): Flow<Result<Reward>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Save to Drive FIRST (Drive is source of truth)
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
                    Log.d("RewardRepository", "Reward updated successfully on Drive: ${updated.id}")
                    emit(Result.Success(updated))
                } else {
                    Log.w("RewardRepository", "Drive updated reward but returned null reward")
                    emit(Result.Error("Reward updated but response was invalid"))
                }
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to update reward on Drive"
                Log.e("RewardRepository", "Failed to update reward on Drive: $errorMsg")
                emit(Result.Error("Failed to save reward update to Drive: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e("RewardRepository", "Update reward failed: ${e.message}")
            emit(Result.Error(e.message ?: "Failed to update reward"))
        }
    }

    /**
     * Delete a reward
     * Drive is the source of truth - delete from Drive FIRST, then remove from local cache
     */
    fun deleteReward(reward: Reward): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Delete from Drive FIRST (Drive is source of truth)
            val response = api.deleteReward(
                request = DeleteRewardRequest(
                    userId = session.userId,
                    rewardId = reward.id
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                // Only remove from local cache AFTER successful Drive deletion
                rewardDao.deleteReward(reward.toEntity())
                Log.d("RewardRepository", "Reward deleted successfully on Drive: ${reward.id}")
                emit(Result.Success(Unit))
            } else {
                val errorMsg = response.body()?.error ?: response.message() ?: "Failed to delete reward on Drive"
                Log.e("RewardRepository", "Failed to delete reward on Drive: $errorMsg")
                emit(Result.Error("Failed to delete reward from Drive: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e("RewardRepository", "Delete reward failed: ${e.message}")
            emit(Result.Error(e.message ?: "Failed to delete reward"))
        }
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
     * Get reward redemptions for the current user, or all pending redemptions for the family
     * Uses local-first approach: returns local data immediately, syncs in background
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
            Log.e("RewardRepository", "Error reading local redemptions: ${e.message}")
            null
        }
        
        // Get first value from Flow (local data)
        val localList = localRedemptions?.let { flow ->
            withContext(Dispatchers.IO) {
                flow.firstOrNull()?.map { it.toDomain() } ?: emptyList()
            }
        } ?: emptyList()
        
        // Sync from server in background (non-blocking) using coroutineScope
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
                        Log.d("RewardRepository", "Synced ${redemptions.size} redemptions from server")
                    }
                } catch (e: Exception) {
                    Log.e("RewardRepository", "Background sync of redemptions failed: ${e.message}")
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
     */
    suspend fun syncRewards() {
        try {
            val session = sessionManager.loadSession()
            if (session != null) {
                val response = api.listRewards(familyId = session.familyId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val rewards = response.body()?.rewards ?: emptyList()
                    rewardDao.insertRewards(rewards.map { it.toEntity() })
                }
            }
        } catch (e: Exception) {
            Log.e("RewardRepository", "Sync rewards failed: ${e.message}")
        }
    }
}
