package com.example.holodex.background

import com.example.holodex.data.db.LikedItemEntity
import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.repository.HolodexRepository
import javax.inject.Inject

class LikesSynchronizer @Inject constructor(
    private val repository: HolodexRepository,
    private val logger: SyncLogger
) : ISynchronizer {
    override val name: String = "LIKES"

    // Timeout period after which we trust the server's state over a local PENDING_DELETE.
    private val PENDING_DELETE_TIMEOUT_MS = 35 * 60 * 1000L // 35 minutes

    override suspend fun synchronize(): Boolean {
        logger.startSection(name)
        try {
            // --- PHASE 0: PRE-SYNC REPAIR (Remains the same) ---
            logger.info("Phase 0: Checking for orphaned local likes to repair...")
            val orphanedLikes = repository.getOrphanedDirtyLikes() // This DAO method needs to be updated
            if (orphanedLikes.isNotEmpty()) {
                logger.info("  -> Found ${orphanedLikes.size} orphaned items. Attempting to repair...")
                for (orphan in orphanedLikes) {

                    if (orphan.itemType == com.example.holodex.data.db.LikedItemType.SONG_SEGMENT) {
                        // --- Logic for Song Segments (Fetch Server ID) ---
                        val videoId = orphan.itemId.substringBeforeLast('_')
                        val startTime = orphan.itemId.substringAfterLast('_').toIntOrNull()

                        if (startTime == null) {
                            logger.warning("  -> SKIPPING repair for song segment with malformed ID: ${orphan.itemId}")
                            continue
                        }

                        val result = repository.fetchVideoAndFindSong(videoId, startTime)
                        if (result != null && result.second?.id != null) {
                            val serverId = result.second!!.id!!
                            val repairedItem = orphan.copy(serverId = serverId)
                            repository.updateLike(repairedItem)
                            logger.logItemAction(LogAction.RECONCILE_SKIP, orphan.actualSongName, orphan.itemId.hashCode().toLong(), serverId, "Successfully repaired orphan song with new serverId")
                        } else {
                            logger.warning("  -> FAILED repair for song '${orphan.actualSongName}'. Could not find matching song on server.")
                        }
                    } else if (orphan.itemType == com.example.holodex.data.db.LikedItemType.VIDEO) {
                        // --- Logic for Videos (Mark as SYNCED) ---
                        // Video likes are local-only and should never be DIRTY. This is a data inconsistency.
                        // The correct repair is to mark it as SYNCED.
                        val repairedItem = orphan.copy(syncStatus = com.example.holodex.data.db.SyncStatus.SYNCED)
                        repository.updateLike(repairedItem)
                        logger.logItemAction(LogAction.RECONCILE_SKIP, orphan.titleSnapshot, orphan.itemId.hashCode().toLong(), null, "Repaired orphan video by marking it SYNCED.")
                    }
                }
            } else {
                logger.info("  -> No orphaned likes found.")
            }
            logger.info("Phase 0 complete.")

            // --- PHASE 1: UPSTREAM (Client informs the server of its desired state) ---
            logger.info("Phase 1: Pushing local changes to server...")
            repository.performUpstreamLikesSync(logger)
            logger.info("Phase 1 complete.")

            // --- PHASE 2: DOWNSTREAM FETCH & STATE RECONCILIATION ---
            logger.info("Phase 2: Fetching states and reconciling...")
            val remoteLikes = repository.getRemoteLikes()
            val allLocalLikes = repository.getAllLocalLikes()
            logger.info("  -> Fetched ${remoteLikes.size} remote likes and ${allLocalLikes.size} local likes.")

            val remoteServerIds = remoteLikes.map { it.id }.toSet()
            val localServerIdMap = allLocalLikes.filter { it.serverId != null }.associateBy { it.serverId!! }
            val now = System.currentTimeMillis()

            val itemsToUpdate = mutableListOf<LikedItemEntity>()
            val itemsToDelete = mutableListOf<String>()

            // --- RULE 3.1: Item exists on server but not locally at all -> ADD LOCALLY ---
            val newFromServer = remoteLikes.filter { !localServerIdMap.containsKey(it.id) }
            if (newFromServer.isNotEmpty()) {
                logger.info("  Found ${newFromServer.size} new likes from another device to insert locally:")
                newFromServer.forEach { p -> logger.logItemAction(LogAction.DOWNSTREAM_INSERT_LOCAL, p.name, null, p.id) }
                repository.insertRemoteLikesAsSynced(newFromServer)
            }

            // Iterate through all local items to apply the other rules.
            for (localItem in allLocalLikes) {
                // Rule 3.2 is implicitly handled by the `newFromServer` filter above.
                // We only need to process SYNCED and PENDING_DELETE items here.

                val serverHasItem = localItem.serverId in remoteServerIds

                when (localItem.syncStatus) {
                    SyncStatus.SYNCED -> {
                        // --- RULE 3.3: Item is SYNCED locally but NOT on server -> DELETE LOCALLY ---
                        if (!serverHasItem && localItem.serverId != null) {
                            itemsToDelete.add(localItem.itemId)
                            logger.logItemAction(LogAction.DOWNSTREAM_DELETE_LOCAL, localItem.actualSongName, localItem.itemId.hashCode().toLong(), localItem.serverId, "Item was removed on another device.")
                        }
                    }
                    SyncStatus.PENDING_DELETE -> {
                        // --- RULE 3.4: Item is PENDING_DELETE and NOT on server -> DELETE LOCALLY (SUCCESS) ---
                        if (!serverHasItem) {
                            itemsToDelete.add(localItem.itemId)
                            logger.logItemAction(LogAction.UPSTREAM_DELETE_SUCCESS, localItem.actualSongName, localItem.itemId.hashCode().toLong(), localItem.serverId, "Deletion confirmed by server.")
                        }
                        // --- RULE 4: PENDING_DELETE has timed out AND is still on server -> REVERT TO SYNCED ---
                        else if (serverHasItem && now - localItem.lastModifiedAt > PENDING_DELETE_TIMEOUT_MS) {
                            itemsToUpdate.add(localItem.copy(syncStatus = SyncStatus.SYNCED))
                            logger.logItemAction(LogAction.RECONCILE_SKIP, localItem.actualSongName, localItem.itemId.hashCode().toLong(), localItem.serverId, "PENDING_DELETE timed out. Server state wins. Reverting to SYNCED.")
                        }
                    }
                    SyncStatus.DIRTY -> {
                        // DIRTY items are handled by the upstream sync and subsequent reconciliation.
                        // We do not need to do anything with them in this loop.
                    }
                }
            }

            // Apply all collected changes to the database at once.
            if (itemsToUpdate.isNotEmpty()) {
                logger.info("  -> Updating ${itemsToUpdate.size} items in the database.")
                repository.updateLikesBatch(itemsToUpdate)
            }
            if (itemsToDelete.isNotEmpty()) {
                logger.info("  -> Deleting ${itemsToDelete.size} items from the database.")
                repository.deleteLikesBatch(itemsToDelete)
            }

            logger.info("Phase 2 complete.")
            logger.endSection(name, success = true)
            return true
        } catch (e: Exception) {
            logger.error(e, "Likes sync failed catastrophically.")
            logger.endSection(name, success = false)
            return false
        }
    }
}