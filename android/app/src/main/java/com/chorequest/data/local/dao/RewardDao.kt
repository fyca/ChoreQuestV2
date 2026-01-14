package com.chorequest.data.local.dao

import androidx.room.*
import com.chorequest.data.local.entities.RewardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {

    @Query("SELECT * FROM rewards WHERE available = 1 ORDER BY pointCost ASC")
    fun getAvailableRewards(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards ORDER BY createdAt DESC")
    fun getAllRewards(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards WHERE id = :rewardId")
    suspend fun getRewardById(rewardId: String): RewardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReward(reward: RewardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRewards(rewards: List<RewardEntity>)

    @Update
    suspend fun updateReward(reward: RewardEntity)

    @Delete
    suspend fun deleteReward(reward: RewardEntity)

    @Query("DELETE FROM rewards")
    suspend fun deleteAllRewards()
}
