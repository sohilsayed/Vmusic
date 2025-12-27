package com.example.holodex.data.repository

import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.db.UserInteractionEntity
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.di.IoDispatcher
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedVideoRepository @Inject constructor(
    private val unifiedDao: UnifiedDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    // --- Optimized Flows ---

    fun getFavorites(): Flow<List<UnifiedDisplayItem>> {
        return unifiedDao.getOptimizedFavoritesFeed().map { list -> list.map { it.toUnifiedDisplayItem() } }
    }

    fun getDownloads(): Flow<List<UnifiedDisplayItem>> {
        return unifiedDao.getOptimizedDownloadsFeed().map { list -> list.map { it.toUnifiedDisplayItem() } }
    }

    fun getHistory(): Flow<List<UnifiedDisplayItem>> {
        return unifiedDao.getOptimizedHistoryFeed().map { list -> list.map { it.toUnifiedDisplayItem() } }
    }

    // --- Channel Logic remains same (uses Projections due to FAV_CHANNEL join complexity not being bottleneck) ---
    fun getFavoriteChannels(): Flow<List<UnifiedDisplayItem>> {
        return unifiedDao.getFavoriteChannels().map { list -> list.map { it.toUnifiedDisplayItem() } }
    }

    fun getFavoriteChannelIds(): Flow<List<String>> {
        return unifiedDao.getFavoriteChannels().map { list -> list.map { it.metadata.id } }
    }

    fun observeLikedItemIds(): Flow<Set<String>> {
        return unifiedDao.getLikedItemIds().map { it.toSet() }
    }

    fun observeDownloadedIds(): Flow<Set<String>> {
        return unifiedDao.getDownloadsFeed().map { list -> list.map { it.metadata.id }.toSet() }.distinctUntilChanged()
    }

    suspend fun getDownloadedIdsSnapshot(): Set<String> {
        return unifiedDao.getAllDownloadsOneShot().filter { it.downloadStatus == "COMPLETED" }.map { it.itemId }.toSet()
    }

    suspend fun getDownloadedItemsIdToPathMap(): Map<String, String> {
        return unifiedDao.getAllDownloadsOneShot()
            .filter { it.downloadStatus == "COMPLETED" && !it.localFilePath.isNullOrBlank() }
            .associate { it.itemId to it.localFilePath!! }
    }

    suspend fun toggleLike(item: PlaybackItem) = withContext(ioDispatcher) {
        val isCurrentlyLiked = unifiedDao.isLiked(item.id) > 0

        if (isCurrentlyLiked) {
            val serverId = unifiedDao.getLikeServerId(item.id)
            if (serverId != null) {
                unifiedDao.softDeleteInteraction(item.id, "LIKE")
            } else {
                unifiedDao.deleteInteraction(item.id, "LIKE")
            }
        } else {
            val isSegment = item.songId != null
            val type = if (isSegment) "SEGMENT" else "VIDEO"
            val parentId = if (isSegment) item.videoId else null

            val metadata = UnifiedMetadataEntity(
                id = item.id,
                title = item.title,
                artistName = item.artistText,
                type = type,
                specificArtUrl = item.artworkUri,
                uploaderAvatarUrl = null,
                duration = item.durationSec,
                channelId = item.channelId,
                description = item.description,
                startSeconds = item.clipStartSec,
                endSeconds = item.clipEndSec,
                parentVideoId = parentId,
                lastUpdatedAt = System.currentTimeMillis()
            )

            unifiedDao.insertMetadataIgnore(metadata)

            val interaction = UserInteractionEntity(
                itemId = item.id,
                interactionType = "LIKE",
                timestamp = System.currentTimeMillis(),
                syncStatus = SyncStatus.DIRTY.name,
                serverId = item.serverUuid
            )
            unifiedDao.upsertInteraction(interaction)
        }
    }

    suspend fun toggleChannelLike(details: ChannelDetails) = withContext(ioDispatcher) {
        val isLiked = unifiedDao.isChannelLiked(details.id) > 0

        if (isLiked) {
            val serverId = unifiedDao.getChannelLikeServerId(details.id)
            if (serverId != null) {
                unifiedDao.softDeleteInteraction(details.id, "FAV_CHANNEL")
            } else {
                unifiedDao.deleteInteraction(details.id, "FAV_CHANNEL")
            }
        } else {
            val meta = UnifiedMetadataEntity(
                id = details.id,
                title = details.name,
                artistName = details.org ?: "",
                type = "CHANNEL",
                specificArtUrl = details.photoUrl,
                uploaderAvatarUrl = details.photoUrl,
                duration = 0,
                channelId = details.id,
                description = details.description,
                org = details.org
            )
            unifiedDao.upsertMetadata(meta)

            val interaction = UserInteractionEntity(
                itemId = details.id,
                interactionType = "FAV_CHANNEL",
                timestamp = System.currentTimeMillis(),
                syncStatus = SyncStatus.DIRTY.name
            )
            unifiedDao.upsertInteraction(interaction)
        }
    }

    suspend fun getChannel(channelId: String): ChannelDetails? = withContext(ioDispatcher) {
        val projection = unifiedDao.getItemByIdOneShot(channelId) ?: return@withContext null
        if (projection.metadata.type != "CHANNEL") return@withContext null

        ChannelDetails(
            id = projection.metadata.id,
            name = projection.metadata.title,
            englishName = null,
            description = projection.metadata.description,
            photoUrl = projection.metadata.specificArtUrl,
            bannerUrl = null,
            org = projection.metadata.artistName,
            suborg = null,
            twitter = null,
            group = null
        )
    }
}