// File: java/com/example/holodex/background/PlaylistSynchronizer.kt
package com.example.holodex.background

import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.repository.HolodexRepository
import timber.log.Timber
import javax.inject.Inject

class PlaylistSynchronizer @Inject constructor(
    private val repository: HolodexRepository,
    private val logger: SyncLogger
) : ISynchronizer {
    override val name: String = "PLAYLISTS"

    override suspend fun synchronize(): Boolean {
        logger.startSection(name)
        try {
            // --- PHASE 1: UPSTREAM (Client -> Server) ---
            logger.info("Phase 1: Pushing local changes to server...")
            repository.performUpstreamPlaylistDeletions(logger)
            repository.performUpstreamPlaylistUpserts(logger)
            logger.info("Phase 1 complete.")

            // --- PHASE 2: FETCH ---
            logger.info("Phase 2: Fetching remote and local states...")
            val remotePlaylists = repository.getRemotePlaylists()
            val localPlaylists = repository.getLocalPlaylists()
            logger.info("  -> Fetched ${remotePlaylists.size} remote playlists and ${localPlaylists.size} local playlists.")

            // --- PHASE 3: METADATA RECONCILIATION ---
            logger.info("Phase 3: Reconciling playlist metadata...")
            val remoteMap = remotePlaylists.associateBy { it.serverId }
            val localMap = localPlaylists.filter { it.serverId != null }.associateBy { it.serverId!! }

            val newFromServer = remotePlaylists.filter { !localMap.containsKey(it.serverId) }
            if (newFromServer.isNotEmpty()) {
                logger.info("  Found ${newFromServer.size} new playlists from server:")
                newFromServer.forEach { p -> logger.logItemAction(LogAction.DOWNSTREAM_INSERT_LOCAL, p.name, null, p.serverId) }
                repository.insertNewSyncedPlaylists(newFromServer)
            }

            val deletedOnServer = localPlaylists.filter {
                it.serverId != null && it.syncStatus == SyncStatus.SYNCED && !remoteMap.containsKey(it.serverId)
            }
            if (deletedOnServer.isNotEmpty()) {
                logger.info("  Found ${deletedOnServer.size} playlists deleted on server:")
                deletedOnServer.forEach { p -> logger.logItemAction(LogAction.DOWNSTREAM_DELETE_LOCAL, p.name, p.playlistId, p.serverId) }
                repository.deleteLocalPlaylists(deletedOnServer.map { it.playlistId })
            }
            logger.info("Phase 3 complete.")


            // --- PHASE 4: CONTENT SYNCHRONIZATION (Timestamp-based) ---
            logger.info("Phase 4: Reconciling song content for all user-owned playlists...")
            val finalLocalPlaylists = repository.getLocalPlaylists().filter { it.serverId != null }

            for (localPlaylist in finalLocalPlaylists) {
                val remotePlaylist = remoteMap[localPlaylist.serverId] ?: continue
                if (localPlaylist.syncStatus == SyncStatus.DIRTY) {
                    logger.info("  -> Skipping content sync for DIRTY playlist '${localPlaylist.name}'.")
                    continue
                }

                val localTimestamp = localPlaylist.last_modified_at
                val remoteTimestamp = remotePlaylist.last_modified_at

                if (localTimestamp != null && remoteTimestamp != null && remoteTimestamp > localTimestamp) {
                    logger.info("  -> Server is newer for playlist '${localPlaylist.name}'. Reconciling local content.")

                    // CRITICAL FIX: Capture count BEFORE any operations
                    val itemCountBefore = repository.getPlaylistItemCount(localPlaylist.playlistId)
                    val localOnlyCountBefore = repository.getLocalOnlyItemCount(localPlaylist.playlistId)

                    Timber.tag("SYNC_DEBUG").i(
                        "BEFORE sync: Playlist '${localPlaylist.name}' has $itemCountBefore total items, $localOnlyCountBefore local-only"
                    )

                    // 1. Get the remote content first.
                    val remoteContent = repository.getRemotePlaylistContent(remotePlaylist.serverId!!)

                    // 2. Perform the SAFE reconciliation of items FIRST. This preserves local-only items.
                    //    This MUST happen before any metadata updates to avoid triggering cascade deletes.
                    repository.reconcileLocalPlaylistItems(localPlaylist.playlistId, remoteContent)

                    // 3. ONLY AFTER the items are safely reconciled, update the parent playlist's metadata.
                    //    This updates the timestamp to match the server, preventing re-sync on next run.
                    repository.updateLocalPlaylistMetadata(localPlaylist.playlistId, remotePlaylist)

                    logger.info("    -> Reconciled local content with ${remoteContent.size} server items.")

                } else {
                    logger.info("  -> Local state for playlist '${localPlaylist.name}' is current. No content sync needed.")
                }
            }
            logger.info("Phase 4 complete.")

            logger.endSection(name, success = true)
            return true
        } catch (e: Exception) {
            logger.error(e, "Playlist sync failed catastrophically.")
            logger.endSection(name, success = false)
            return false
        }
    }
}