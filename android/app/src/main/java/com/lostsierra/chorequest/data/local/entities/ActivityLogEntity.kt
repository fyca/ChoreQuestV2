package com.lostsierra.chorequest.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lostsierra.chorequest.domain.models.*

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val timestamp: String,
    val actorId: String,
    val actorName: String,
    val actorRole: UserRole,
    val actionType: ActivityActionType,
    val targetUserId: String?,
    val targetUserName: String?,
    val details: ActivityDetails,
    val referenceId: String?,
    val referenceType: String?,
    val metadata: ActivityMetadata // Always non-null in entity (we provide defaults)
)

fun ActivityLogEntity.toDomain(): ActivityLog {
    return ActivityLog(
        id = id,
        timestamp = timestamp,
        actorId = actorId,
        actorName = actorName,
        actorRole = actorRole,
        actionType = actionType,
        targetUserId = targetUserId,
        targetUserName = targetUserName,
        details = details,
        referenceId = referenceId,
        referenceType = referenceType,
        metadata = metadata
    )
}

fun ActivityLog.toEntity(): ActivityLogEntity {
    return ActivityLogEntity(
        id = id,
        timestamp = timestamp,
        actorId = actorId,
        actorName = actorName,
        actorRole = actorRole,
        actionType = actionType,
        targetUserId = targetUserId,
        targetUserName = targetUserName,
        details = details,
        referenceId = referenceId,
        referenceType = referenceType,
        metadata = metadata ?: ActivityMetadata(
            deviceType = DeviceType.UNKNOWN,
            appVersion = "1.0.0",
            location = null
        )
    )
}
