package com.chorequest.data.repository

import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.ActivityLogDao
import com.chorequest.data.local.entities.toEntity
import com.chorequest.data.local.entities.toDomain
import com.chorequest.data.remote.ChoreQuestApi
import com.chorequest.domain.models.ActivityLog
import com.chorequest.utils.Constants
import com.chorequest.utils.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for activity log operations
 */
@Singleton
class ActivityLogRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val activityLogDao: ActivityLogDao,
    private val sessionManager: SessionManager
) {

    /**
     * Get recent activity logs
     */
    fun getRecentLogs(limit: Int = 50): Flow<List<ActivityLog>> {
        return activityLogDao.getRecentLogs(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get logs for a specific user
     */
    fun getLogsForUser(userId: String): Flow<List<ActivityLog>> {
        return activityLogDao.getLogsForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get logs by action type
     */
    fun getLogsByType(actionType: String): Flow<List<ActivityLog>> {
        return activityLogDao.getLogsByType(actionType).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Fetch activity logs from server and cache locally
     */
    suspend fun fetchActivityLogs(
        userId: String? = null,
        actionType: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        page: Int = 1,
        pageSize: Int = 50
    ): Flow<Result<List<ActivityLog>>> = flow {
        emit(Result.Loading)
        
        try {
            val session = sessionManager.loadSession()
            if (session == null) {
                emit(Result.Error("No active session"))
                return@flow
            }

            val response = api.getActivityLogs(
                userId = userId,
                actionType = actionType,
                startDate = startDate,
                endDate = endDate,
                page = page,
                pageSize = pageSize
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.logs != null) {
                    // Cache logs locally
                    val entities = body.logs.map { it.toEntity() }
                    activityLogDao.insertLogs(entities)
                    
                    emit(Result.Success(body.logs))
                } else {
                    emit(Result.Error(body?.error ?: "Failed to fetch activity logs"))
                }
            } else {
                emit(Result.Error("Server error: ${response.code()}"))
            }
        } catch (e: Exception) {
            emit(Result.Error("Error fetching activity logs: ${e.message}"))
        }
    }

    /**
     * Clear old activity logs from local cache
     */
    suspend fun clearOldLogs(cutoffDate: String) {
        activityLogDao.deleteOldLogs(cutoffDate)
    }

    /**
     * Clear all activity logs from local cache
     */
    suspend fun clearAllLogs() {
        activityLogDao.deleteAllLogs()
    }
}
