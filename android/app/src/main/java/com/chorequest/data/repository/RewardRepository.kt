package com.chorequest.data.repository

import android.content.Context
import android.util.Log
import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.RewardDao
import com.chorequest.data.local.entities.toEntity
import com.chorequest.data.local.entities.toDomain
import com.chorequest.data.remote.*
import com.chorequest.domain.models.Reward
import com.chorequest.utils.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing reward data
 */
@Singleton
class RewardRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val rewardDao: RewardDao,
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
     */
    fun createReward(reward: Reward): Flow<Result<Reward>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Save locally first
            rewardDao.insertReward(reward.toEntity())

            // Sync with Drive-backed Apps Script
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
                    // Replace local with server version (server is source of truth)
                    rewardDao.deleteReward(reward.toEntity())
                    rewardDao.insertReward(created.toEntity())
                    emit(Result.Success(created))
                } else {
                    emit(Result.Success(reward))
                }
            } else {
                Log.w("RewardRepository", "Server sync failed for reward creation: ${response.body()?.error ?: response.message()}")
                emit(Result.Success(reward)) // keep local optimistic result
            }
        } catch (e: Exception) {
            Log.e("RewardRepository", "Create reward failed: ${e.message}")
            // If we saved locally but sync failed, still success
            emit(Result.Success(reward))
        }
    }

    /**
     * Update an existing reward
     */
    fun updateReward(reward: Reward): Flow<Result<Reward>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Update locally first
            rewardDao.updateReward(reward.toEntity())

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
                    rewardDao.updateReward(updated.toEntity())
                    emit(Result.Success(updated))
                } else {
                    emit(Result.Success(reward))
                }
            } else {
                Log.w("RewardRepository", "Server sync failed for reward update: ${response.body()?.error ?: response.message()}")
                emit(Result.Success(reward))
            }
        } catch (e: Exception) {
            Log.e("RewardRepository", "Update reward failed: ${e.message}")
            emit(Result.Success(reward))
        }
    }

    /**
     * Delete a reward
     */
    fun deleteReward(reward: Reward): Flow<Result<Unit>> = flow {
        emit(Result.Loading)
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            // Delete locally first
            rewardDao.deleteReward(reward.toEntity())

            val response = api.deleteReward(
                request = DeleteRewardRequest(
                    userId = session.userId,
                    rewardId = reward.id
                )
            )
            if (!response.isSuccessful || response.body()?.success != true) {
                Log.w("RewardRepository", "Server sync failed for reward deletion: ${response.body()?.error ?: response.message()}")
            }
            emit(Result.Success(Unit))
        } catch (e: Exception) {
            Log.e("RewardRepository", "Delete reward failed: ${e.message}")
            emit(Result.Success(Unit))
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
