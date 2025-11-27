package com.example.holodex.background

import com.example.holodex.data.api.AuthenticatedMusicdexApiService
import com.example.holodex.data.api.LikeRequest
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.SyncRepository
import javax.inject.Inject

class LikesSynchronizer @Inject constructor(
    private val syncRepository: SyncRepository,
    private val holodexRepository: HolodexRepository, // Needed for Orphan lookup (fetchVideoAndFindSong)
    private val apiService: AuthenticatedMusicdexApiService,
    private val logger: SyncLogger
) : ISynchronizer {

    override val name: String = "LIKES"
    private val TYPE = "LIKE"

    // Timeout period after which we trust the server's state over a local PENDING_DELETE.
    private val PENDING_DELETE_TIMEOUT_MS = 35 * 60 * 1000L // 35 minutes

    override suspend fun synchronize(): Boolean {
        logger.startSection(name)
        try {
            // ====================================================================================
            // PHASE 0: PRE-SYNC REPAIR
            // Fix items marked DIRTY but missing a serverId (happens if added offline/error)
            // ====================================================================================
            logger.info("Phase 0: Checking for orphaned local likes to repair...")
            val dirtyItems = syncRepository.getDirtyItems(TYPE)
            val orphanedLikes = dirtyItems.filter { it.serverId == null }

            if (orphanedLikes.isNotEmpty()) {
                logger.info("  -> Found ${orphanedLikes.size} orphaned items. Attempting to repair...")
                for (orphan in orphanedLikes) {
                    // Parse the composite ID (videoId_start)
                    val videoId = orphan.itemId.substringBeforeLast('_')
                    val startTime = orphan.itemId.substringAfterLast('_').toIntOrNull()

                    if (startTime != null) {
                        // Song Segment: Fetch remote data to find the real Server UUID
                        val result = holodexRepository.fetchVideoAndFindSong(videoId, startTime)
                        val song = result?.second

                        if (song?.id != null) {
                            // Found it! Update local DB with the Server ID so Phase 1 can sync it.
                            // We use 'upsertInteraction' via Repository to update just the serverId
                            val repaired = orphan.copy(serverId = song.id)
                            syncRepository.updateServerId(repaired.itemId, TYPE, song.id)
                            // Note: We keep it DIRTY so Phase 1 picks it up to send to server

                            logger.logItemAction(LogAction.RECONCILE_SKIP, "Song_${orphan.itemId}", null, song.id, "Repaired orphan song UUID")
                        } else {
                            logger.warning("  -> FAILED repair for '${orphan.itemId}'. Could not find matching song on server.")
                        }
                    } else {
                        // Full Video: Videos don't use UUIDs for likes, they use the VideoID itself as the key.
                        // If serverId is null, set it to videoId.
                        syncRepository.updateServerId(orphan.itemId, TYPE, orphan.itemId)
                        logger.logItemAction(LogAction.RECONCILE_SKIP, "Video_${orphan.itemId}", null, orphan.itemId, "Repaired orphan video ID")
                    }
                }
            }
            logger.info("Phase 0 complete.")

            // ====================================================================================
            // PHASE 1: UPSTREAM (Local -> Server)
            // ====================================================================================
            logger.info("Phase 1: Pushing local changes to server...")

            // 1. Handle Deletions (PENDING_DELETE)
            val pendingDeletes = syncRepository.getPendingDeleteItems(TYPE)
            for (item in pendingDeletes) {
                if (item.serverId == null) {
                    // Local-only item, just delete
                    syncRepository.confirmDeletion(item.itemId, TYPE)
                    continue
                }

                val response = apiService.deleteLike(LikeRequest(song_id = item.serverId))
                if (response.isSuccessful || response.code() == 404) {
                    syncRepository.confirmDeletion(item.itemId, TYPE)
                    logger.logItemAction(LogAction.UPSTREAM_DELETE_SUCCESS, item.itemId, null, item.serverId)
                } else {
                    logger.logItemAction(LogAction.UPSTREAM_DELETE_FAILED, item.itemId, null, item.serverId, "Code: ${response.code()}")
                }
            }

            // 2. Handle Additions (DIRTY)
            // Re-fetch dirty items because Phase 0 might have fixed some
            val readyToUpload = syncRepository.getDirtyItems(TYPE).filter { it.serverId != null }

            for (item in readyToUpload) {
                val response = apiService.addLike(LikeRequest(song_id = item.serverId!!))
                if (response.isSuccessful) {
                    syncRepository.markAsSynced(item.itemId, TYPE, item.serverId!!)
                    logger.logItemAction(LogAction.UPSTREAM_UPSERT_SUCCESS, item.itemId, null, item.serverId)
                } else {
                    logger.logItemAction(LogAction.UPSTREAM_UPSERT_FAILED, item.itemId, null, item.serverId, "Code: ${response.code()}")
                }
            }
            logger.info("Phase 1 complete.")

            // ====================================================================================
            // PHASE 2: DOWNSTREAM (Server -> Local)
            // ====================================================================================
            logger.info("Phase 2: Fetching states and reconciling...")

            // 1. Fetch Remote
            val allRemoteLikes = mutableListOf<com.example.holodex.data.api.LikedSongApiDto>()
            var page = 1
            while (true) {
                val res = apiService.getLikes(page = page, paginated = true)
                if (!res.isSuccessful) throw Exception("Failed to fetch likes page $page")
                val body = res.body() ?: break
                allRemoteLikes.addAll(body.content)
                if (page >= body.page_count) break
                page++
            }

            // 2. Fetch Local SYNCED items (Ignore Dirty/Pending)
            val localSynced = syncRepository.getSyncedItems(TYPE)

            val remoteIdMap = allRemoteLikes.associateBy { it.id } // Key = Server UUID
            val localServerIdMap = localSynced.associateBy { it.serverId } // Key = Server UUID

            // --- RULE 1: Item on Server, Not Local -> INSERT ---
            val newFromServer = allRemoteLikes.filter { !localServerIdMap.containsKey(it.id) }
            if (newFromServer.isNotEmpty()) logger.info("  Found ${newFromServer.size} new likes from server.")

            for (remote in newFromServer) {
                // Construct Metadata (We must have this to insert interaction)
                val meta = UnifiedMetadataEntity(
                    id = remote.video_id, // We use videoId (or composite) as the Metadata Key
                    title = remote.name,
                    artistName = remote.original_artist ?: "Unknown",
                    type = "SEGMENT", // API likes are usually segments
                    specificArtUrl = remote.art,
                    uploaderAvatarUrl = remote.channel?.photo,
                    duration = (remote.end - remote.start).toLong(),
                    channelId = remote.channel_id,
                    description = null,
                    startSeconds = remote.start.toLong(),
                    endSeconds = remote.end.toLong(),
                    parentVideoId = remote.video_id,
                    lastUpdatedAt = System.currentTimeMillis()
                )

                // The Interaction ID must match how we generate IDs locally (composite)
                val localItemId = "${remote.video_id}_${remote.start}"

                syncRepository.insertRemoteItem(localItemId, TYPE, remote.id, meta)
                logger.logItemAction(LogAction.DOWNSTREAM_INSERT_LOCAL, remote.name, null, remote.id)
            }

            // --- RULE 2: Item Synced Locally, Not on Server -> DELETE ---
            // Only check items that claim to be SYNCED. If Dirty/Pending, ignore.
            val deletedOnServer = localSynced.filter { it.serverId != null && !remoteIdMap.containsKey(it.serverId) }

            if (deletedOnServer.isNotEmpty()) logger.info("  Found ${deletedOnServer.size} likes removed on server.")

            for (local in deletedOnServer) {
                syncRepository.removeRemoteItem(local.itemId, TYPE)
                logger.logItemAction(LogAction.DOWNSTREAM_DELETE_LOCAL, local.itemId, null, local.serverId)
            }

            // --- RULE 3: Timeout Logic (Zombie Prevention) ---
            // If an item is PENDING_DELETE for too long, and it still exists on server,
            // assume the delete failed silently or was reverted by another device.
            val pendingToCheck = syncRepository.getPendingDeleteItems(TYPE)
            val now = System.currentTimeMillis()

            for (pending in pendingToCheck) {
                if (pending.serverId != null && remoteIdMap.containsKey(pending.serverId)) {
                    // It's pending delete locally, but server still has it.
                    if (now - pending.timestamp > PENDING_DELETE_TIMEOUT_MS) {
                        // Revert to SYNCED
                        syncRepository.markAsSynced(pending.itemId, TYPE, pending.serverId!!)
                        logger.logItemAction(LogAction.RECONCILE_SKIP, pending.itemId, null, pending.serverId, "Pending delete timed out. Reverted to SYNCED.")
                    }
                }
            }

            logger.endSection(name, true)
            return true
        } catch (e: Exception) {
            logger.error(e, "Likes Sync Failed")
            logger.endSection(name, false)
            return false
        }
    }
}