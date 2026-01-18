package com.chorequest.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chorequest.domain.models.*

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String?,
    val role: UserRole,
    val isPrimaryParent: Boolean,
    val avatarUrl: String?,
    val pointsBalance: Int,
    val canEarnPoints: Boolean,
    val authToken: String,
    val tokenVersion: Int,
    val devices: List<Device>,
    val createdAt: String,
    val createdBy: String,
    val settings: UserSettings,
    val stats: UserStats,
    val birthdate: String? = null
)

fun UserEntity.toDomain(): User {
    return User(
        id = id,
        name = name,
        email = email,
        role = role,
        isPrimaryParent = isPrimaryParent,
        avatarUrl = avatarUrl,
        pointsBalance = pointsBalance,
        canEarnPoints = canEarnPoints,
        authToken = authToken,
        tokenVersion = tokenVersion,
        devices = devices,
        createdAt = createdAt,
        createdBy = createdBy,
        settings = settings,
        stats = stats,
        birthdate = birthdate
    )
}

fun User.toEntity(): UserEntity {
    return UserEntity(
        id = id,
        name = name,
        email = email,
        role = role,
        isPrimaryParent = isPrimaryParent,
        avatarUrl = avatarUrl,
        pointsBalance = pointsBalance,
        canEarnPoints = canEarnPoints,
        authToken = authToken,
        tokenVersion = tokenVersion,
        devices = devices,
        createdAt = createdAt,
        createdBy = createdBy,
        settings = settings,
        stats = stats,
        birthdate = birthdate
    )
}
