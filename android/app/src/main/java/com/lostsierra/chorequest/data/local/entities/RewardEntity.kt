package com.lostsierra.chorequest.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lostsierra.chorequest.domain.models.Reward

@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val pointCost: Int,
    val imageUrl: String?,
    val available: Boolean,
    val quantity: Int?,
    val createdBy: String,
    val redeemedCount: Int,
    val createdAt: String
)

fun RewardEntity.toDomain(): Reward {
    return Reward(
        id = id,
        title = title,
        description = description,
        pointCost = pointCost,
        imageUrl = imageUrl,
        available = available,
        quantity = quantity,
        createdBy = createdBy,
        redeemedCount = redeemedCount,
        createdAt = createdAt
    )
}

fun Reward.toEntity(): RewardEntity {
    return RewardEntity(
        id = id,
        title = title,
        description = description,
        pointCost = pointCost,
        imageUrl = imageUrl,
        available = available,
        quantity = quantity,
        createdBy = createdBy,
        redeemedCount = redeemedCount,
        createdAt = createdAt
    )
}
