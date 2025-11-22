// File: java/com/example/holodex/background/HistorySynchronizer.kt

package com.example.holodex.background

import com.example.holodex.auth.TokenManager
import com.example.holodex.data.db.HistoryDao
import com.example.holodex.data.db.SyncMetadataDao
import com.example.holodex.data.db.SyncMetadataEntity
import com.example.holodex.data.db.mappers.toHistoryItemEntity
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.viewmodel.mappers.toVideoShell
import java.time.Instant
import javax.inject.Inject

class HistorySynchronizer @Inject constructor(
    private val repository: HolodexRepository,
    private val historyDao: HistoryDao,
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
            logger.endSection(name, success = true) // Success because there's nothing to do
            return true
        }

        try {
            logger.info("Phase 1: Upstream (handled by real-time tracking).")

            logger.info("Phase 2: Fetching remote history playlist...")
            val historyPlaylistId = ":history[user_id=$userId]"
            val remoteResult = repository.getFullPlaylistContent(historyPlaylistId)

            if (remoteResult.isFailure) {
                throw remoteResult.exceptionOrNull() ?: Exception("Failed to fetch remote history")
            }

            val remotePlaylist = remoteResult.getOrThrow()
            val remoteTimestampStr = remotePlaylist.updatedAt
            if (remoteTimestampStr.isNullOrBlank()) {
                throw IllegalStateException("Remote history playlist has no 'updated_at' timestamp.")
            }

            val remoteTimestamp = Instant.parse(remoteTimestampStr).toEpochMilli()
            val localTimestamp = syncMetadataDao.getLastSyncTimestamp(METADATA_KEY) ?: 0L
            logger.info("  -> Remote Timestamp: $remoteTimestamp | Local Timestamp: $localTimestamp")

            if (remoteTimestamp > localTimestamp) {
                logger.info("Phase 3: Server state is newer. Updating local cache.")

                // --- START OF FIX: Generate unique, ordered timestamps ---
                val baseTimestamp = System.currentTimeMillis()

                val remoteHistoryItems = remotePlaylist.content?.mapIndexedNotNull { index, song ->
                    val videoShell = song.toVideoShell(remotePlaylist.title)
                    // Pass the synthetic timestamp to the mapper
                    song.toHistoryItemEntity(videoShell, baseTimestamp - index)
                } ?: emptyList()
                // --- END OF FIX ---

                historyDao.clearAll()
                // The DAO needs an insertAll method for efficiency
                // If it doesn't have one, this loop is the fallback.
                remoteHistoryItems.forEach { historyDao.insert(it) }

                syncMetadataDao.setLastSyncTimestamp(
                    SyncMetadataEntity(
                        dataType = METADATA_KEY,
                        lastSyncTimestamp = remoteTimestamp
                    )
                )
                logger.info("  -> Successfully updated local history with ${remoteHistoryItems.size} items and set new timestamp.")
            } else {
                logger.info("Phase 3: Local state is up-to-date or newer. No downstream sync needed.")
            }

            logger.endSection(name, success = true)
            return true

        } catch (e: Exception) {
            logger.error(e, "History sync failed catastrophically.")
            logger.endSection(name, success = false)
            return false
        }
    }
}