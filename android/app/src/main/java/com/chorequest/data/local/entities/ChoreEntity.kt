package com.chorequest.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chorequest.domain.models.*

@Entity(tableName = "chores")
data class ChoreEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val assignedTo: List<String>,
    val createdBy: String,
    val pointValue: Int,
    val dueDate: String?,
    val recurring: RecurringSchedule?,
    val subtasks: List<Subtask>,
    val status: ChoreStatus,
    val photoProof: String?,
    val completedBy: String?,
    val completedAt: String?,
    val verifiedBy: String?,
    val verifiedAt: String?,
    val createdAt: String,
    val color: String?,
    val icon: String?
)

/**
 * Convert ChoreEntity to domain Chore model
 */
fun ChoreEntity.toDomain(): Chore {
    return Chore(
        id = id,
        title = title,
        description = description,
        assignedTo = assignedTo,
        createdBy = createdBy,
        pointValue = pointValue,
        dueDate = dueDate,
        recurring = recurring,
        subtasks = subtasks,
        status = status,
        photoProof = photoProof,
        completedBy = completedBy,
        completedAt = completedAt,
        verifiedBy = verifiedBy,
        verifiedAt = verifiedAt,
        createdAt = createdAt,
        color = color,
        icon = icon
    )
}

fun Chore.toEntity(): ChoreEntity {
    return ChoreEntity(
        id = id,
        title = title,
        description = description,
        assignedTo = assignedTo,
        createdBy = createdBy,
        pointValue = pointValue,
        dueDate = dueDate,
        recurring = recurring,
        subtasks = subtasks,
        status = status,
        photoProof = photoProof,
        completedBy = completedBy,
        completedAt = completedAt,
        verifiedBy = verifiedBy,
        verifiedAt = verifiedAt,
        createdAt = createdAt,
        color = color,
        icon = icon
    )
}
