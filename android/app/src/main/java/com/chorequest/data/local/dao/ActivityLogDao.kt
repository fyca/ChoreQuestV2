package com.chorequest.data.local.dao

import androidx.room.*
import com.chorequest.data.local.entities.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE actorId = :userId OR targetUserId = :userId ORDER BY timestamp DESC")
    fun getLogsForUser(userId: String): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE actionType = :actionType ORDER BY timestamp DESC")
    fun getLogsByType(actionType: String): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<ActivityLogEntity>)

    @Query("DELETE FROM activity_logs")
    suspend fun deleteAllLogs()

    @Query("DELETE FROM activity_logs WHERE timestamp < :cutoffDate")
    suspend fun deleteOldLogs(cutoffDate: String)
}
