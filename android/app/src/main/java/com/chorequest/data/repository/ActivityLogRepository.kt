package com.chorequest.data.repository

import android.util.Log
import com.chorequest.data.local.SessionManager
import com.chorequest.data.local.dao.ActivityLogDao
import com.chorequest.data.local.entities.toEntity
import com.chorequest.data.local.entities.toDomain
import com.chorequest.data.remote.ChoreQuestApi
import com.chorequest.domain.models.ActivityLog
import com.chorequest.domain.models.ActivityLogData
import com.chorequest.utils.Constants
import com.chorequest.utils.Result
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for activity log operations
 */
@Singleton
class ActivityLogRepository @Inject constructor(
    private val api: ChoreQuestApi,
    private val activityLogDao: ActivityLogDao,
    private val sessionManager: SessionManager,
    private val gson: Gson,
    private val driveApiService: com.chorequest.data.drive.DriveApiService,
    private val tokenManager: com.chorequest.data.drive.TokenManager
) {
    companion object {
        private const val TAG = "ActivityLogRepository"
    }

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
     * Helper: Read activity logs from Drive using direct API
     */
    private suspend fun readActivityLogsFromDrive(accessToken: String, folderId: String): ActivityLogData? {
        return try {
            val fileName = "activity_log.json"
            val fileId = driveApiService.findFileByName(accessToken, folderId, fileName)
            
            if (fileId != null) {
                val jsonContent = driveApiService.readFileContent(accessToken, fileId)
                gson.fromJson(jsonContent, ActivityLogData::class.java)
            } else {
                // File doesn't exist, return empty
                ActivityLogData(logs = emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading activity logs from Drive", e)
            null
        }
    }

    /**
     * Filter logs based on query parameters
     */
    private fun filterLogs(
        logs: List<ActivityLog>,
        userId: String?,
        actionType: String?,
        startDate: String?,
        endDate: String?
    ): List<ActivityLog> {
        var filtered = logs
        
        // Filter by user
        if (userId != null) {
            filtered = filtered.filter { 
                it.actorId == userId || it.targetUserId == userId 
            }
        }
        
        // Filter by action type
        if (actionType != null) {
            filtered = filtered.filter { it.actionType.name == actionType }
        }
        
        // Filter by date range
        if (startDate != null || endDate != null) {
            filtered = filtered.filter { log ->
                try {
                    val logTimestamp = Instant.parse(log.timestamp)
                    val start = startDate?.let { Instant.parse(it) }
                    val end = endDate?.let { Instant.parse(it) }
                    
                    (start == null || logTimestamp >= start) && 
                    (end == null || logTimestamp <= end)
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing date in filter", e)
                    true // Include if date parsing fails
                }
            }
        }
        
        return filtered
    }

    /**
     * Apply pagination to logs
     */
    private fun paginateLogs(logs: List<ActivityLog>, page: Int, pageSize: Int): List<ActivityLog> {
        val sortedLogs = logs.sortedByDescending { it.timestamp } // Most recent first
        val startIndex = (page - 1) * pageSize
        val endIndex = startIndex + pageSize
        
        if (startIndex >= sortedLogs.size) {
            return emptyList()
        }
        
        return sortedLogs.subList(
            startIndex.coerceAtLeast(0), 
            endIndex.coerceAtMost(sortedLogs.size)
        )
    }

    /**
     * Fetch activity logs from server and cache locally
     * Uses direct Drive API for faster performance (no cold start)
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

            // Try direct Drive API first
            val accessToken = tokenManager.getValidAccessToken()
            if (accessToken != null) {
                try {
                    Log.d(TAG, "Using direct Drive API to fetch activity logs")
                    val folderId = session.driveWorkbookLink
                    
                    // Read activity logs from Drive
                    val activityLogData = readActivityLogsFromDrive(accessToken, folderId)
                    if (activityLogData != null) {
                        // Filter logs based on query parameters
                        var filteredLogs = filterLogs(
                            activityLogData.logs,
                            userId,
                            actionType,
                            startDate,
                            endDate
                        )
                        
                        // Apply pagination
                        val paginatedLogs = paginateLogs(filteredLogs, page, pageSize)
                        
                        // Cache logs locally
                        val entities = paginatedLogs.map { it.toEntity() }
                        activityLogDao.insertLogs(entities)
                        
                        Log.d(TAG, "Fetched ${paginatedLogs.size} activity logs via Drive API")
                        emit(Result.Success(paginatedLogs))
                        return@flow
                    } else {
                        Log.w(TAG, "Failed to read activity logs from Drive, falling back to Apps Script")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using direct Drive API, falling back to Apps Script", e)
                }
            } else {
                Log.d(TAG, "No access token available, using Apps Script")
            }

            // Fallback to Apps Script
            val response = api.getActivityLogs(
                familyId = session.familyId,
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
            Log.e(TAG, "Error fetching activity logs", e)
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
