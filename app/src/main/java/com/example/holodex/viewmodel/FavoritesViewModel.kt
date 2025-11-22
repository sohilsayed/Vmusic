// File: java/com/example/holodex/viewmodel/FavoritesViewModel.kt
package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.db.FavoriteChannelEntity
import com.example.holodex.data.db.LikedItemDao
import com.example.holodex.data.db.LikedItemEntity
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.data.db.LocalFavoriteEntity
import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.model.HolodexChannelMin
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.LocalRepository
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.util.formatDurationSeconds
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import javax.inject.Inject

// --- State, SideEffect, and Helper Enums ---
enum class StorageLocation { LIKED_ITEMS, LOCAL_FAVORITES }
enum class ItemCategory { SYNCABLE_SEGMENT, VIRTUAL_SEGMENT, FULL_VIDEO }

data class FavoritesState(
    val likedItemsMap: Map<String, StorageLocation> = emptyMap(),
    val unifiedLikedSegments: ImmutableList<UnifiedDisplayItem> = persistentListOf(), // Changed
    val unifiedFavoritedVideos: ImmutableList<UnifiedDisplayItem> = persistentListOf(), // Changed
    val favoriteChannels: ImmutableList<Any> = persistentListOf(), // Changed
    val isLoading: Boolean = true
)

sealed class FavoritesSideEffect {
    data class ShowToast(val message: String) : FavoritesSideEffect()
}

@UnstableApi
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val holodexRepository: HolodexRepository,
    private val localRepository: LocalRepository,
    private val likedItemDao: LikedItemDao,
    private val downloadRepository: DownloadRepository
) : ViewModel(), ContainerHost<FavoritesState, FavoritesSideEffect> {

    override val container: Container<FavoritesState, FavoritesSideEffect> =
        container(FavoritesState()) {
            // This is the "onCreate" block. We start an intent that will run for the ViewModel's lifecycle.
            intent {
                // repeatOnSubscription is the key for collecting flows in a lifecycle-aware way.
                // It starts collecting when the UI is visible and stops when it's not.
                repeatOnSubscription {
                    val likedItemsFlow = combine(
                        holodexRepository.getObservableLikedSongSegments(),
                        holodexRepository.getFavoritedVideosPaged(0, 1000),
                        localRepository.getLocalFavorites()
                    ) { syncedSegments, syncedVideos, localFavs ->
                        Triple(syncedSegments, syncedVideos, localFavs)
                    }

                    val channelFlow = combine(
                        holodexRepository.getFavoriteChannels(),
                        localRepository.getAllExternalChannels()
                    ) { syncedChannels, localChannels ->
                        syncedChannels to localChannels
                    }

                    combine(
                        likedItemsFlow,
                        channelFlow,
                        downloadRepository.getAllDownloads()
                            .map { list -> list.map { it.videoId }.toSet() }
                    ) { (syncedSegments, syncedVideos, localFavs), (syncedChannels, localChannels), downloadedIds ->

                        val map = buildMap {
                            syncedSegments.forEach { put(it.itemId, StorageLocation.LIKED_ITEMS) }
                            syncedVideos.forEach { put(it.itemId, StorageLocation.LIKED_ITEMS) }
                            localFavs.forEach { put(it.itemId, StorageLocation.LOCAL_FAVORITES) }
                        }

                        val unifiedSegments = (
                                syncedSegments.map {
                                    it.toUnifiedDisplayItem(
                                        downloadedIds.contains(
                                            it.itemId
                                        )
                                    )
                                } +
                                        localFavs.filter { it.isSegment }
                                            .map { it.toUnifiedDisplayItem(downloadedIds.contains(it.itemId)) }
                                ).sortedByDescending { it.stableId }

                        val unifiedVideos = (
                                syncedVideos.map { it.toUnifiedDisplayItem(false) } +
                                        localFavs.filter { !it.isSegment }
                                            .map { it.toUnifiedDisplayItem(false) }
                                ).sortedByDescending { it.stableId }

                        val unifiedChannels =
                            (syncedChannels + localChannels).sortedByDescending { if (it is FavoriteChannelEntity) it.favoritedAtTimestamp else 0L }

                        // This transformation creates the new state object
                        FavoritesState(
                            likedItemsMap = map,
                            unifiedLikedSegments = unifiedSegments.toImmutableList(),
                            unifiedFavoritedVideos = unifiedVideos.toImmutableList(),
                            favoriteChannels = unifiedChannels.toImmutableList(),
                            isLoading = false
                        )
                    }.collect { newState ->
                        // This reduce block is now correctly inside the intent's scope.
                        reduce { newState }
                    }
                }
            }
        }

    fun toggleLike(item: PlaybackItem) = intent {
        try {
            val likeId = getLikeIdForPlaybackItem(item)
            val storageLocation = state.likedItemsMap[likeId]

            if (storageLocation != null) {
                // UNLIKE PATH
                when (storageLocation) {
                    StorageLocation.LIKED_ITEMS -> holodexRepository.removeLikedItem(likeId)
                    StorageLocation.LOCAL_FAVORITES -> localRepository.removeLocalFavorite(likeId)
                }
                postSideEffect(FavoritesSideEffect.ShowToast("Removed from favorites"))
            } else {
                // LIKE PATH
                when (categorizeItem(item)) {
                    ItemCategory.SYNCABLE_SEGMENT -> addSyncedSongSegment(item)
                    ItemCategory.VIRTUAL_SEGMENT -> addLocalVirtualSegment(item)
                    ItemCategory.FULL_VIDEO -> addLocalHolodexVideo(item)
                }
                postSideEffect(FavoritesSideEffect.ShowToast("Added to favorites"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle like for item: ${item.title}")
            postSideEffect(FavoritesSideEffect.ShowToast("Error: Could not update favorite"))
        }
    }

    fun toggleFavoriteChannel(videoItem: HolodexVideoItem) = intent {
        val channelId = videoItem.channel.id ?: return@intent
        val isFavorited =
            state.favoriteChannels.any { (it is FavoriteChannelEntity && it.id == channelId) }
        if (isFavorited) {
            holodexRepository.removeFavoriteChannel(channelId)
        } else {
            holodexRepository.addFavoriteChannel(videoItem)
        }
    }

    fun toggleFavoriteChannelByDetails(details: ChannelDetails) = intent {
        val isFavorited =
            state.favoriteChannels.any { (it is FavoriteChannelEntity && it.id == details.id) }
        if (isFavorited) {
            holodexRepository.removeFavoriteChannel(details.id)
        } else {
            val videoShell = HolodexVideoItem(
                id = "channel_favorite_${details.id}", title = details.name, type = "placeholder",
                topicId = null, availableAt = "", publishedAt = null, duration = 0, status = "",
                channel = HolodexChannelMin(
                    id = details.id, name = details.name, englishName = details.englishName,
                    org = details.org, type = "vtuber", photoUrl = details.photoUrl
                ), songcount = null, description = details.description, songs = null
            )
            holodexRepository.addFavoriteChannel(videoShell)
        }
    }

    fun getLikeIdForPlaybackItem(item: PlaybackItem): String {
        return if (item.clipStartSec != null) {
            LikedItemEntity.generateSongItemId(item.videoId, item.clipStartSec.toInt())
        } else {
            LikedItemEntity.generateVideoItemId(item.videoId)
        }
    }

    private fun categorizeItem(item: PlaybackItem): ItemCategory {
        return when {
            item.isExternal -> ItemCategory.VIRTUAL_SEGMENT
            item.clipStartSec != null -> {
                if (item.clipStartSec > 0) ItemCategory.SYNCABLE_SEGMENT
                else ItemCategory.VIRTUAL_SEGMENT
            }

            else -> ItemCategory.FULL_VIDEO
        }
    }

    private suspend fun addSyncedSongSegment(item: PlaybackItem) {
        try {
            val itemId = getLikeIdForPlaybackItem(item)
            val existingItem = likedItemDao.getLikedItem(itemId)
            if (existingItem?.syncStatus == SyncStatus.PENDING_DELETE) {
                likedItemDao.updateStatusAndTimestamp(
                    itemId,
                    SyncStatus.DIRTY,
                    System.currentTimeMillis()
                )
            } else if (existingItem == null) {
                val songForLike = HolodexSong(
                    name = item.title, start = item.clipStartSec!!.toInt(),
                    end = item.clipEndSec?.toInt() ?: 0,
                    itunesId = null, artUrl = item.artworkUri, videoId = item.videoId,
                    originalArtist = item.originalArtist
                )
                val videoContext = HolodexVideoItem(
                    id = item.videoId,
                    title = item.albumText ?: item.title,
                    type = "stream",
                    topicId = null,
                    publishedAt = null,
                    availableAt = "",
                    duration = item.durationSec,
                    status = "past",
                    channel = HolodexChannelMin(
                        id = item.channelId, name = item.artistText, englishName = null,
                        org = null, type = "vtuber", photoUrl = null
                    ),
                    songcount = null,
                    description = item.description,
                    songs = null
                )
                holodexRepository.addLikedSongSegment(videoContext, songForLike)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to add synced song segment")
            intent { postSideEffect(FavoritesSideEffect.ShowToast("Error: ${e.message}")) }
        }
    }

    private suspend fun addLocalVirtualSegment(item: PlaybackItem) {
        try {
            val localFavorite = LocalFavoriteEntity(
                itemId = getLikeIdForPlaybackItem(item),
                videoId = item.videoId,
                channelId = item.channelId,
                title = item.title,
                artistText = item.artistText,
                artworkUrl = item.artworkUri,
                durationSec = item.durationSec,
                isSegment = true,
                songStartSec = item.clipStartSec?.toInt(),
                songEndSec = item.clipEndSec?.toInt()
            )
            localRepository.addLocalFavorite(localFavorite)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add local virtual segment like")
        }
    }

    private suspend fun addLocalHolodexVideo(item: PlaybackItem) {
        try {
            val entity = LikedItemEntity(
                itemId = getLikeIdForPlaybackItem(item), videoId = item.videoId,
                itemType = LikedItemType.VIDEO, serverId = null, titleSnapshot = item.title,
                artistTextSnapshot = item.artistText, albumTextSnapshot = item.albumText,
                artworkUrlSnapshot = item.artworkUri, descriptionSnapshot = item.description,
                channelIdSnapshot = item.channelId, durationSecSnapshot = item.durationSec,
                syncStatus = SyncStatus.SYNCED
            )
            likedItemDao.insert(entity)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add local Holodex video like")
        }
    }

    private fun LocalFavoriteEntity.toUnifiedDisplayItem(isDownloaded: Boolean): UnifiedDisplayItem {
        return UnifiedDisplayItem(
            stableId = "local_fav_${this.itemId}",
            playbackItemId = this.itemId,
            videoId = this.videoId,
            channelId = this.channelId,
            title = this.title,
            artistText = this.artistText,
            artworkUrls = listOfNotNull(this.artworkUrl),
            durationText = formatDurationSeconds(this.durationSec),
            isSegment = this.isSegment,
            songCount = null,
            isDownloaded = isDownloaded,
            isLiked = true,
            itemTypeForPlaylist = if (this.isSegment) LikedItemType.SONG_SEGMENT else LikedItemType.VIDEO,
            songStartSec = this.songStartSec,
            songEndSec = this.songEndSec,
            originalArtist = this.artistText,
            isExternal = true
        )
    }
}