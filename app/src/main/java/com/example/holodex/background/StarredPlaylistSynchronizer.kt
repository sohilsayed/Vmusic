// File: java/com/example/holodex/background/StarredPlaylistSynchronizer.kt (NEW FILE)
package com.example.holodex.background

import com.example.holodex.data.repository.PlaylistRepository
import javax.inject.Inject

class StarredPlaylistSynchronizer @Inject constructor(
    private val repository: PlaylistRepository,
    private val logger: SyncLogger
) : ISynchronizer {
    override val name: String = "STARRED_PLAYLISTS"

    override suspend fun synchronize(): Boolean {
        logger.startSection(name)
        try {
            // --- PHASE 1: UPSTREAM ---
            logger.info("Phase 1: Pushing local changes to server...")
            repository.performUpstreamStarredPlaylistsSync(logger)
            logger.info("Phase 1 complete.")

            // --- PHASE 2: SMART SERVER SYNC ---
            logger.info("Phase 2: Fetching server state and reconciling local data...")
            val remoteStarred = repository.getRemoteStarredPlaylists()
            logger.info("  -> Fetched ${remoteStarred.size} starred playlists from server.")

            val unsyncedCount = repository.getLocalUnsyncedStarredPlaylistsCount()
            if (unsyncedCount > 0) {
                logger.info("  -> Preserving $unsyncedCount locally-changed items.")
            }

            val deletedCount = repository.deleteLocalSyncedStarredPlaylists()
            logger.info("  -> Wiped $deletedCount SYNCED items from local database.")

            repository.insertRemoteStarredPlaylistsAsSynced(remoteStarred)
            logger.info("  -> Paved local database with ${remoteStarred.size} fresh items from server.")
            logger.info("Phase 2 complete.")

            logger.endSection(name, success = true)
            return true
        } catch (e: Exception) {
            logger.error(e, "Starred Playlists sync failed catastrophically.")
            logger.endSection(name, success = false)
            return false
        }
    }
}