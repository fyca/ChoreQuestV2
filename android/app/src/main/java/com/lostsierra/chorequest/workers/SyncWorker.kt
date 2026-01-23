package com.lostsierra.chorequest.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lostsierra.chorequest.data.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "chorequest_sync_work"
        const val KEY_FORCE_SYNC = "force_sync"
    }

    override suspend fun doWork(): Result {
        // Check if this is a forced sync (manual trigger) or automatic sync
        val forceSync = inputData.getBoolean(KEY_FORCE_SYNC, false)
        Log.i(TAG, "Starting sync (forceSync=$forceSync)...")

        return try {
            val success = syncRepository.syncAll(forceSync = forceSync)
            
            if (success) {
                // Persist last synced timestamp for UI (dashboard "Synced Xm ago") and future throttling
                syncRepository.updateLastSyncTime(System.currentTimeMillis())
                Log.i(TAG, "Sync completed successfully (lastSynced updated)")
                Result.success()
            } else {
                Log.w(TAG, "Sync failed, will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            Result.failure()
        }
    }
}
