// File: java/com/example/holodex/background/SyncCoordinator.kt (NEW FILE)
package com.example.holodex.background

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor(
    private val synchronizers: Set<@JvmSuppressWildcards ISynchronizer>,
    private val logger: SyncLogger
) {
    suspend fun run(): Boolean {
        logger.info("===== Starting Full Synchronization Run =====")
        var allSucceeded = true

        try {
            coroutineScope {
                val results = synchronizers.map { synchronizer ->
                    async {
                        // We will run some syncs sequentially if they are dependent in the future
                        // For now, all are independent.
                        synchronizer.synchronize()
                    }
                }.map { it.await() }

                if (results.contains(false)) {
                    allSucceeded = false
                }
            }
        } catch (e: Exception) {
            logger.error(e, "The SyncCoordinator caught an unhandled exception.")
            allSucceeded = false
        }

        logger.info("===== Full Synchronization Run Finished. Overall Success: $allSucceeded =====")
        return allSucceeded
    }
}