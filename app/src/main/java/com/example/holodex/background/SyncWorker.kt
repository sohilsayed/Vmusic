// File: java/com/example/holodex/background/SyncWorker.kt (MODIFIED)
package com.example.holodex.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.holodex.auth.TokenManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    // --- MODIFICATION: Inject SyncCoordinator, not PlaylistRepository ---
    private val syncCoordinator: SyncCoordinator,
    private val tokenManager: TokenManager
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SyncWorker"
        const val MAX_RUN_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result {
        Timber.i("$TAG: Starting synchronization work using SyncCoordinator.")

        if (tokenManager.getJwt() == null) {
            Timber.i("$TAG: User not logged in. Sync work is not required. Finishing successfully.")
            return Result.success()
        }

        return try {
            // --- MODIFICATION: Call the coordinator ---
            val success = syncCoordinator.run()

            if (success) {
                Timber.i("$TAG: Synchronization work completed successfully.")
                Result.success()
            } else {
                // The coordinator's log will have the details. We just handle the retry logic.
                Timber.w("$TAG: SyncCoordinator reported failure on attempt $runAttemptCount.")
                if (runAttemptCount < MAX_RUN_ATTEMPTS) {
                    Timber.w("$TAG: Scheduling a retry.")
                    Result.retry()
                } else {
                    Timber.e("$TAG: Maximum retry attempts reached. Failing the work.")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: SyncWorker caught an unhandled exception.")
            if (runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.failure()
        }
    }
}