// File: java/com/example/holodex/background/FavoriteChannelSynchronizer.kt (NEW FILE)
package com.example.holodex.background

import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.repository.HolodexRepository
import javax.inject.Inject

class FavoriteChannelSynchronizer @Inject constructor(
    private val repository: HolodexRepository,
    private val logger: SyncLogger
) : ISynchronizer {
    override val name: String = "FAVORITE_CHANNELS"

    override suspend fun synchronize(): Boolean {
        logger.startSection(name)
        try {
            // --- PHASE 1: UPSTREAM ---
            logger.info("Phase 1: Pushing local changes to server...")
            repository.performUpstreamFavoriteChannelsSync(logger)
            logger.info("Phase 1 complete.")

            // --- PHASE 2: FETCH ---
            logger.info("Phase 2: Fetching remote and local states...")
            val remoteFavs = repository.getRemoteFavoriteChannels()
            val localFavs = repository.getLocalFavoriteChannels()
            logger.info("  -> Fetched ${remoteFavs.size} remote favorites and ${localFavs.size} local favorites.")

            // --- PHASE 3: MERGE & RECONCILE ---
            logger.info("Phase 3: Reconciling favorite channels...")
            val remoteMap = remoteFavs.associateBy { it.id }
            val localMap = localFavs.associateBy { it.id }

            // Find new favorites from server to insert locally
            val newFromServer = remoteFavs.filter { !localMap.containsKey(it.id) }
            if (newFromServer.isNotEmpty()) {
                logger.info("  Found ${newFromServer.size} new favorites from server:")
                newFromServer.forEach { p -> logger.logItemAction(LogAction.DOWNSTREAM_INSERT_LOCAL, p.name, null, p.id) }
                repository.insertNewSyncedFavoriteChannels(newFromServer)
            }

            // Find favorites deleted on server to delete locally
            val deletedOnServer = localFavs.filter { it.syncStatus == SyncStatus.SYNCED && !remoteMap.containsKey(it.id) }
            if (deletedOnServer.isNotEmpty()) {
                logger.info("  Found ${deletedOnServer.size} favorites deleted on server:")
                deletedOnServer.forEach { p -> logger.logItemAction(LogAction.DOWNSTREAM_DELETE_LOCAL, p.name, p.id.hashCode().toLong(), p.id) }
                repository.deleteLocalFavoriteChannels(deletedOnServer.map { it.id })
            }

            logger.info("Phase 3 complete.")
            logger.endSection(name, success = true)
            return true
        } catch (e: Exception) {
            logger.error(e, "Favorite Channels sync failed catastrophically.")
            logger.endSection(name, success = false)
            return false
        }
    }
}