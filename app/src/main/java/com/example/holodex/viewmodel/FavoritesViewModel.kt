package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.playback.domain.model.PlaybackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

enum class StorageLocation { LIKED_ITEMS, LOCAL_FAVORITES }

data class FavoritesState(
    val likedItemsMap: Map<String, StorageLocation> = emptyMap(),
    val unifiedLikedSegments: ImmutableList<UnifiedDisplayItem> = persistentListOf(),
    val unifiedFavoritedVideos: ImmutableList<UnifiedDisplayItem> = persistentListOf(),
    val favoriteChannels: ImmutableList<UnifiedDisplayItem> = persistentListOf(),
    val isLoading: Boolean = true
)

sealed class FavoritesSideEffect {
    data class ShowToast(val message: String) : FavoritesSideEffect()
}

@UnstableApi
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val unifiedRepository: UnifiedVideoRepository
) : ViewModel(), ContainerHost<FavoritesState, FavoritesSideEffect> {

    override val container = container<FavoritesState, FavoritesSideEffect>(FavoritesState()) {
        intent {
            combine(
                unifiedRepository.getFavorites(),
                unifiedRepository.getFavoriteChannels(),
                unifiedRepository.observeLikedItemIds()
            ) { favorites, channels, likedIds ->

                // *** FIX: Add Channel IDs to the map ***
                val channelIds = channels.map { it.playbackItemId }.toSet()
                val allLikedIds = likedIds + channelIds

                val map = allLikedIds.associateWith { StorageLocation.LIKED_ITEMS }

                val videos = favorites.filter { !it.isSegment }.toImmutableList()
                val segments = favorites.filter { it.isSegment }.toImmutableList()
                val channelList = channels.toImmutableList()

                FavoritesState(
                    likedItemsMap = map,
                    unifiedFavoritedVideos = videos,
                    unifiedLikedSegments = segments,
                    favoriteChannels = channelList,
                    isLoading = false
                )
            }.collect { newState ->
                reduce { newState }
            }
        }
    }

    fun toggleLike(item: PlaybackItem) = intent {
        try {
            unifiedRepository.toggleLike(item)
        } catch (e: Exception) {
            postSideEffect(FavoritesSideEffect.ShowToast("Error updating favorites"))
        }
    }

    fun toggleFavoriteChannel(video: HolodexVideoItem) = intent {
        val details = ChannelDetails(
            id = video.channel.id ?: "",
            name = video.channel.name,
            englishName = video.channel.englishName,
            photoUrl = video.channel.photoUrl,
            org = video.channel.org,
            // Fill defaults for fields not present in VideoItem
            description = null,
            bannerUrl = null,
            suborg = null,
            twitter = null,
            group = null
        )

        try {
            unifiedRepository.toggleChannelLike(details)
            postSideEffect(FavoritesSideEffect.ShowToast("Channel updated"))
        } catch (e: Exception) {
            postSideEffect(FavoritesSideEffect.ShowToast("Failed to update channel"))
        }
    }

    // Keep this overload for the Channel Screen
    fun toggleFavoriteChannel(details: ChannelDetails) = intent {
        try {
            unifiedRepository.toggleChannelLike(details)
            postSideEffect(FavoritesSideEffect.ShowToast("Channel updated"))
        } catch (e: Exception) {
            postSideEffect(FavoritesSideEffect.ShowToast("Failed to update channel"))
        }
    }
}