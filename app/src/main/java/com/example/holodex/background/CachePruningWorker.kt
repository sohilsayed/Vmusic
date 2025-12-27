package com.example.holodex.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.holodex.data.db.UnifiedDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class CachePruningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val unifiedDao: UnifiedDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            // 24 Hour Retention Policy for "Window Shopping" items
            val threshold = now - TimeUnit.HOURS.toMillis(24)

            Timber.i("CachePruningWorker: Pruning metadata older than $threshold")

            // This runs the SQL DELETE query we added to UnifiedDao in Phase 1
            unifiedDao.pruneOrphanedMetadata(threshold)

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Cache pruning failed")
            Result.retry()
        }
    }
}