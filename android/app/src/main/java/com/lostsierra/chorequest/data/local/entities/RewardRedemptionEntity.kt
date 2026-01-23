package com.lostsierra.chorequest.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lostsierra.chorequest.domain.models.RewardRedemption
import com.lostsierra.chorequest.domain.models.RewardRedemptionStatus

@Entity(tableName = "reward_redemptions")
data class RewardRedemptionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val userName: String? = null, // Nullable - may not be in batch response
    val rewardId: String,
    val rewardTitle: String? = null, // Nullable - may not be in batch response
    val status: RewardRedemptionStatus,
    val requestedAt: String,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val deniedBy: String? = null,
    val deniedAt: String? = null,
    val denialReason: String? = null,
    val completedAt: String? = null,
    val pointCost: Int
)

fun RewardRedemptionEntity.toDomain(): RewardRedemption {
    return RewardRedemption(
        id = id,
        userId = userId,
        userName = userName ?: "Unknown User", // Provide default if null
        rewardId = rewardId,
        rewardTitle = rewardTitle ?: "Unknown Reward", // Provide default if null
        status = status,
        requestedAt = requestedAt,
        approvedBy = approvedBy,
        approvedAt = approvedAt,
        deniedBy = deniedBy,
        deniedAt = deniedAt,
        denialReason = denialReason,
        completedAt = completedAt,
        pointCost = pointCost
    )
}

fun RewardRedemption.toEntity(): RewardRedemptionEntity {
    return RewardRedemptionEntity(
        id = id,
        userId = userId,
        userName = userName,
        rewardId = rewardId,
        rewardTitle = rewardTitle,
        status = status,
        requestedAt = requestedAt,
        approvedBy = approvedBy,
        approvedAt = approvedAt,
        deniedBy = deniedBy,
        deniedAt = deniedAt,
        denialReason = denialReason,
        completedAt = completedAt,
        pointCost = pointCost
    )
}
