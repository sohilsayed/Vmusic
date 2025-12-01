// File: java/com/example/holodex/background/HistorySynchronizer.kt
package com.example.holodex.background

import androidx.room.withTransaction
import com.example.holodex.auth.TokenManager
import com.example.holodex.data.db.AppDatabase
import com.example.holodex.data.db.SyncMetadataDao
import com.example.holodex.data.db.SyncMetadataEntity
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.db.UserInteractionEntity
import com.example.holodex.data.repository.HolodexRepository
import java.time.Instant
import javax.inject.Inject

class HistorySynchronizer @Inject constructor(
    private val repository: HolodexRepository,
    private val unifiedDao: UnifiedDao,
    private val database: AppDatabase, // For transaction support
    private val syncMetadataDao: SyncMetadataDao,
    private val tokenManager: TokenManager,
    private val logger: SyncLogger
) : ISynchronizer {
    override val name: String = "HISTORY"
    private val METADATA_KEY = "history_last_sync_timestamp"

    override suspend fun synchronize(): Boolean {
        logger.startSection(name)
        val userId = tokenManager.getUserId()
        if (userId.isNullOrBlank()) {
            logger.warning("User ID not found, skipping history sync.")
            logger.endSection(name, success = true)
            return true
        }

        try {
            logger.info("Phase 1: Fetching remote history playlist...")
            // Holodex stores history as a special playlist
            val historyPlaylistId = ":history[user_id=$userId]"
            val remoteResult = repository.getFullPlaylistContent(historyPlaylistId)

            if (remoteResult.isFailure) {
                throw remoteResult.exceptionOrNull() ?: Exception("Failed to fetch remote history")
            }

            val remotePlaylist = remoteResult.getOrThrow()
            val remoteTimestampStr = remotePlaylist.updatedAt

            // If remote has no timestamp, we force sync anyway if content exists
            val remoteTimestamp = if (!remoteTimestampStr.isNullOrBlank()) {
                Instant.parse(remoteTimestampStr).toEpochMilli()
            } else {
                System.currentTimeMillis()
            }

            val localTimestamp = syncMetadataDao.getLastSyncTimestamp(METADATA_KEY) ?: 0L
            logger.info("  -> Remote TS: $remoteTimestamp | Local TS: $localTimestamp")

            // Matching existing logic: If server is newer OR has content, we overwrite local to match server.
            if (remoteTimestamp > localTimestamp || (remotePlaylist.content?.isNotEmpty() == true)) {
                logger.info("Phase 2: Updating local history cache from server.")

                val baseTimestamp = System.currentTimeMillis()
                val remoteSongs = remotePlaylist.content ?: emptyList()

                database.withTransaction {
                    // 1. Clear existing local history to ensure exact match with server order/content
                    // This matches the "current system" logic of wiping the old table.
                    unifiedDao.deleteAllInteractionsByType("HISTORY")

                    // 2. Insert new items
                    remoteSongs.forEachIndexed { index, song ->
                        if (!song.channelId.isNullOrBlank()) {
                            val itemId = "${song.videoId}_${song.start}"
                            val playedAt = baseTimestamp - index // Preserve server order using timestamp

                            // A. Upsert Metadata (Safe insert)
                            val metadata = UnifiedMetadataEntity(
                                id = itemId,
                                title = song.name,
                                artistName = song.channel.name,
                                type = "SEGMENT",
                                specificArtUrl = song.artUrl,
                                uploaderAvatarUrl = song.channel.photoUrl,
                                duration = (song.end - song.start).toLong(),
                                channelId = song.channelId,
                                parentVideoId = song.videoId,
                                startSeconds = song.start.toLong(),
                                endSeconds = song.end.toLong(),
                                lastUpdatedAt = System.currentTimeMillis()
                            )
                            unifiedDao.upsertMetadata(metadata)

                            // B. Insert Interaction
                            val interaction = UserInteractionEntity(
                                itemId = itemId,
                                interactionType = "HISTORY",
                                timestamp = playedAt,
                                syncStatus = "SYNCED",
                                serverId = song.id // Server UUID for the history item if available
                            )
                            unifiedDao.upsertInteraction(interaction)
                        }
                    }

                    // 3. Update Sync Timestamp
                    syncMetadataDao.setLastSyncTimestamp(
                        SyncMetadataEntity(
                            dataType = METADATA_KEY,
                            lastSyncTimestamp = remoteTimestamp
                        )
                    )
                }
                logger.info("  -> Successfully synced ${remoteSongs.size} history items.")
            } else {
                logger.info("Phase 2: Local history is up-to-date.")
            }

            logger.endSection(name, success = true)
            return true

        } catch (e: Exception) {
            logger.error(e, "History sync failed.")
            logger.endSection(name, success = false)
            return false
        }
    }
}