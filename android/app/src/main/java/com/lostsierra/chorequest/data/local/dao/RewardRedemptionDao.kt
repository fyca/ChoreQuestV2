package com.lostsierra.chorequest.data.local.dao

import androidx.room.*
import com.lostsierra.chorequest.data.local.entities.RewardRedemptionEntity
import com.lostsierra.chorequest.domain.models.RewardRedemptionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardRedemptionDao {

    @Query("SELECT * FROM reward_redemptions ORDER BY requestedAt DESC")
    fun getAllRedemptions(): Flow<List<RewardRedemptionEntity>>

    @Query("SELECT * FROM reward_redemptions WHERE userId = :userId ORDER BY requestedAt DESC")
    fun getRedemptionsByUserId(userId: String): Flow<List<RewardRedemptionEntity>>

    @Query("SELECT * FROM reward_redemptions WHERE status = :status ORDER BY requestedAt DESC")
    fun getRedemptionsByStatus(status: RewardRedemptionStatus): Flow<List<RewardRedemptionEntity>>

    @Query("SELECT * FROM reward_redemptions WHERE userId = :userId AND status = :status ORDER BY requestedAt DESC")
    fun getRedemptionsByUserIdAndStatus(userId: String, status: RewardRedemptionStatus): Flow<List<RewardRedemptionEntity>>

    @Query("SELECT * FROM reward_redemptions WHERE id = :redemptionId")
    suspend fun getRedemptionById(redemptionId: String): RewardRedemptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRedemption(redemption: RewardRedemptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRedemptions(redemptions: List<RewardRedemptionEntity>)

    @Update
    suspend fun updateRedemption(redemption: RewardRedemptionEntity)

    @Delete
    suspend fun deleteRedemption(redemption: RewardRedemptionEntity)

    @Query("DELETE FROM reward_redemptions")
    suspend fun deleteAllRedemptions()
}
