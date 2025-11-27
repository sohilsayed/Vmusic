package com.example.holodex.data.repository

import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.db.UserInteractionEntity
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.di.IoDispatcher
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.viewmodel.UnifiedDisplayItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedVideoRepository @Inject constructor(
    private val unifiedDao: UnifiedDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO // Use Qualifier
) {

    // ============================================================================================
    // 1. UI DATA STREAMS
    // ============================================================================================

    fun getFavorites(): Flow<List<UnifiedDisplayItem>> {
        return unifiedDao.getFavorites().map { list ->
            list.map { it.toUnifiedDisplayItem() }
        }
    }

    fun getFavoriteChannels(): Flow<List<UnifiedDisplayItem>> {
        return unifiedDao.getFavoriteChannels().map { list ->
            list.map { it.toUnifiedDisplayItem() }
        }
    }
    fun getFavoriteChannelIds(): Flow<List<String>> {
        return unifiedDao.getFavoriteChannels().map { list ->
            list.map { projection -> projection.metadata.id }
        }
    }
    fun observeLikedItemIds(): Flow<Set<String>> {
        return unifiedDao.getLikedItemIds().map { it.toSet() }
    }
    fun getDownloads(): Flow<List<UnifiedDisplayItem>> {
        return unifiedDao.getDownloads().map { list ->
            list.map { it.toUnifiedDisplayItem() }
        }
    }
    // ============================================================================================
    // 2. USER ACTIONS (TOGGLES)
    // ============================================================================================

    suspend fun toggleLike(item: PlaybackItem) = withContext(Dispatchers.IO) {
        val isCurrentlyLiked = unifiedDao.isLiked(item.id) > 0

        if (isCurrentlyLiked) {
            // Check if synced before hard deleting
            val serverId = unifiedDao.getLikeServerId(item.id)
            if (serverId != null) {
                unifiedDao.softDeleteInteraction(item.id, "LIKE")
            } else {
                unifiedDao.deleteInteraction(item.id, "LIKE")
            }
        } else {
            // 1. Metadata
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
            unifiedDao.upsertMetadata(metadata)

            // 2. Interaction
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

    suspend fun toggleChannelLike(channel: ChannelDetails) = withContext(Dispatchers.IO) {
        // We check if ANY interaction of type FAV_CHANNEL exists for this ID
        val isLiked = unifiedDao.isChannelLiked(channel.id) > 0

        if (isLiked) {
            // Soft delete if synced, hard delete if not (logic inside dao or here)
            // For simplicity, let's assume soft delete support for channels too
            val serverId = unifiedDao.getChannelLikeServerId(channel.id)
            if (serverId != null) {
                unifiedDao.softDeleteInteraction(channel.id, "FAV_CHANNEL")
            } else {
                unifiedDao.deleteInteraction(channel.id, "FAV_CHANNEL")
            }
        } else {
            val meta = UnifiedMetadataEntity(
                id = channel.id,
                title = channel.name,
                artistName = channel.org ?: "",
                type = "CHANNEL",
                specificArtUrl = channel.photoUrl,
                uploaderAvatarUrl = channel.photoUrl,
                duration = 0,
                channelId = channel.id,
                description = channel.description
            )
            unifiedDao.upsertMetadata(meta)

            val interaction = UserInteractionEntity(
                itemId = channel.id,
                interactionType = "FAV_CHANNEL",
                timestamp = System.currentTimeMillis(),
                syncStatus = SyncStatus.DIRTY.name
            )
            unifiedDao.upsertInteraction(interaction)
        }
    }

    suspend fun getChannel(channelId: String): com.example.holodex.data.model.discovery.ChannelDetails? = withContext(Dispatchers.IO) {
        // We look for metadata of type 'CHANNEL' with this ID
        // We can use the existing DAO method 'getItemByIdOneShot' if you added it,
        // or just filter the flow (slower), or add a specific query.
        // Ideally, add 'getMetadataById(id)' to UnifiedDao.

        // Assuming you added getItemByIdOneShot to UnifiedDao as per previous steps:
        val projection = unifiedDao.getItemByIdOneShot(channelId) ?: return@withContext null

        if (projection.metadata.type != "CHANNEL") return@withContext null

        com.example.holodex.data.model.discovery.ChannelDetails(
            id = projection.metadata.id,
            name = projection.metadata.title,
            englishName = null, // Metadata only stores one title
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