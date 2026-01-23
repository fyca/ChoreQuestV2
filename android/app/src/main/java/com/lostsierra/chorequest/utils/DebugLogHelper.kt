package com.lostsierra.chorequest.utils

import android.util.Log
import com.lostsierra.chorequest.data.remote.ChoreQuestApi
import com.lostsierra.chorequest.data.remote.DebugLogsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper utility to fetch and log Apps Script debug logs to Android logcat
 */
object DebugLogHelper {
    private const val TAG = "AppsScriptDebug"
    
    /**
     * Fetch debug logs from Apps Script and log them to logcat
     */
    suspend fun fetchAndLogDebugLogs(api: ChoreQuestApi, limit: Int = 50) {
        try {
            Log.d(TAG, "Fetching debug logs from Apps Script (limit=$limit)...")
            withContext(Dispatchers.IO) {
                val response = api.getDebugLogs(limit = limit)
                
                Log.d(TAG, "Debug logs response: code=${response.code()}, isSuccessful=${response.isSuccessful}")
                
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d(TAG, "Response body: success=${body?.success}, count=${body?.count}, logsCount=${body?.logs?.size}")
                    
                    if (body?.success == true && body.logs != null) {
                        if (body.logs.isEmpty()) {
                            Log.w(TAG, "=== Apps Script Debug Logs: No logs found (buffer might be empty) ===")
                        } else {
                            Log.d(TAG, "=== Apps Script Debug Logs (${body.count} total, showing ${body.logs.size}) ===")
                            
                            body.logs.forEach { logEntry ->
                                val level = when (logEntry.level.uppercase()) {
                                    "ERROR" -> Log.ERROR
                                    "WARN" -> Log.WARN
                                    "INFO" -> Log.INFO
                                    "DEBUG" -> Log.DEBUG
                                    else -> Log.VERBOSE
                                }
                                
                                val message = "[${logEntry.timestamp}] ${logEntry.level}: ${logEntry.message}"
                                
                                if (logEntry.data != null && logEntry.data.isNotEmpty()) {
                                    val dataStr = logEntry.data.entries.joinToString(", ") { 
                                        "${it.key}=${it.value}" 
                                    }
                                    Log.println(level, TAG, "$message | Data: $dataStr")
                                } else {
                                    Log.println(level, TAG, message)
                                }
                            }
                            
                            Log.d(TAG, "=== End Apps Script Debug Logs ===")
                        }
                    } else {
                        Log.w(TAG, "Failed to fetch debug logs: success=${body?.success}, error=${body?.error ?: "Unknown error"}")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Error fetching debug logs: code=${response.code()}, message=${response.message()}, errorBody=$errorBody")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching debug logs", e)
        }
    }
}
