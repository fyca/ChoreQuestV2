package com.chorequest.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chorequest.data.local.dao.*
import com.chorequest.data.local.entities.*

@Database(
    entities = [
        ChoreEntity::class,
        RewardEntity::class,
        UserEntity::class,
        ActivityLogEntity::class,
        TransactionEntity::class,
        RewardRedemptionEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ChoreQuestDatabase : RoomDatabase() {
    abstract fun choreDao(): ChoreDao
    abstract fun rewardDao(): RewardDao
    abstract fun userDao(): UserDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun transactionDao(): TransactionDao
    abstract fun rewardRedemptionDao(): RewardRedemptionDao
}
