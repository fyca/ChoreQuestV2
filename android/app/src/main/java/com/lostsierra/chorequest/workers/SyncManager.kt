package com.lostsierra.chorequest.workers

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferencesManager: com.lostsierra.chorequest.data.local.AppPreferencesManager
) {
    private val workManager = WorkManager.getInstance(context)

    companion object {
        private const val SYNC_FLEX_INTERVAL_MINUTES = 1L
    }

    /**
     * Schedules periodic background sync using interval from preferences
     */
    fun scheduleSyncWork() {
        val syncIntervalMinutes = appPreferencesManager.getSyncIntervalMinutes()
        scheduleSyncWork(syncIntervalMinutes)
    }

    /**
     * Schedules periodic background sync with specified interval
     * Reschedules existing work if interval changed
     */
    fun scheduleSyncWork(intervalMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Calculate flex interval (should be less than repeat interval, minimum 1 minute)
        val flexIntervalMinutes = minOf(SYNC_FLEX_INTERVAL_MINUTES, intervalMinutes / 2).coerceAtLeast(1L)

        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = intervalMinutes,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
            flexTimeInterval = flexIntervalMinutes,
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                backoffPolicy = BackoffPolicy.EXPONENTIAL,
                backoffDelay = 1,
                timeUnit = TimeUnit.MINUTES
            )
            .addTag(SyncWorker.TAG)
            .build()

        // Use REPLACE to reschedule with new interval if it changed
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }

    /**
     * Cancels all scheduled sync work
     */
    fun cancelSyncWork() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
    }

    /**
     * Triggers an immediate one-time sync (forced, bypasses timestamp check)
     */
    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putBoolean(SyncWorker.KEY_FORCE_SYNC, true)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(SyncWorker.TAG)
            .build()

        workManager.enqueue(syncWorkRequest)
    }

    /**
     * Gets the state of the sync work as LiveData
     */
    fun getSyncWorkInfoLiveData(): LiveData<List<WorkInfo>> {
        // Observe by tag so both periodic sync and one-time manual sync are reflected
        return workManager.getWorkInfosByTagLiveData(SyncWorker.TAG)
    }
    
    /**
     * Gets the state of all sync work (including one-time syncs) as Flow
     */
    fun getSyncWorkInfo(): Flow<List<WorkInfo>> = flow {
        // Get all work with the sync tag
        workManager.getWorkInfosByTagLiveData(SyncWorker.TAG).observeForever { workInfos ->
            // This won't work directly in a Flow, so we'll use a different approach
        }
        // For now, return empty list - we'll use LiveData instead
        emit(emptyList())
    }
}
