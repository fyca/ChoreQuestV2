package com.lostsierra.chorequest.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lostsierra.chorequest.domain.models.Transaction
import com.lostsierra.chorequest.domain.models.TransactionType

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: TransactionType,
    val points: Int,
    val reason: String,
    val referenceId: String,
    val timestamp: String
)

fun TransactionEntity.toDomain(): Transaction {
    return Transaction(
        id = id,
        userId = userId,
        type = type,
        points = points,
        reason = reason,
        referenceId = referenceId,
        timestamp = timestamp
    )
}

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        userId = userId,
        type = type,
        points = points,
        reason = reason,
        referenceId = referenceId,
        timestamp = timestamp
    )
}
