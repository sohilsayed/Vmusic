package com.example.holodex.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.data.model.discovery.DiscoveryChannel
import com.example.holodex.data.repository.FeedRepository
import com.example.holodex.data.repository.PlaylistRepository
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toVideoShell // <--- ADDED IMPORT
import com.example.holodex.viewmodel.state.BrowseFilterState
import com.example.holodex.viewmodel.state.ViewTypePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

data class FullListState(
    val items: List<Any> = emptyList(),
    val isLoadingInitial: Boolean = true,
    val isLoadingMore: Boolean = false,
    val endOfList: Boolean = false,
    val currentOffset: Int = 0
)

sealed class FullListSideEffect {
    data class ShowToast(val message: String) : FullListSideEffect()
}

@HiltViewModel
class FullListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val feedRepository: FeedRepository,
    private val unifiedRepository: UnifiedVideoRepository
) : ContainerHost<FullListState, FullListSideEffect>, ViewModel() {

    companion object {
        const val CATEGORY_TYPE_ARG = "category"
        const val ORG_ARG = "org"
        private const val PAGE_SIZE = 50
    }

    val categoryType: MusicCategoryType = MusicCategoryType.valueOf(
        savedStateHandle.get<String>(CATEGORY_TYPE_ARG) ?: MusicCategoryType.TRENDING.name
    )
    private val organization: String = URLDecoder.decode(
        savedStateHandle.get<String>(ORG_ARG) ?: "All Vtubers",
        StandardCharsets.UTF_8.toString()
    )

    override val container = container<FullListState, FullListSideEffect>(FullListState()) {
        loadMore(isInitialLoad = true)
    }

    fun loadMore(isInitialLoad: Boolean = false) = intent {
        if (!isInitialLoad && (state.isLoadingMore || state.endOfList)) return@intent

        reduce { state.copy(isLoadingInitial = isInitialLoad, isLoadingMore = !isInitialLoad) }

        if (categoryType == MusicCategoryType.RECENT_STREAMS || categoryType == MusicCategoryType.UPCOMING_MUSIC) {
            loadFromStore(isInitialLoad)
        } else {
            loadFromLegacyRepository(isInitialLoad)
        }
    }

    private fun loadFromStore(isInitialLoad: Boolean) = intent {
        val filter = when(categoryType) {
            MusicCategoryType.UPCOMING_MUSIC -> BrowseFilterState.create(ViewTypePreset.UPCOMING_STREAMS, organization)
            else -> BrowseFilterState.create(ViewTypePreset.LATEST_STREAMS, organization)
        }

        feedRepository.getFeed(
            filter = filter,
            offset = state.currentOffset,
            refresh = isInitialLoad
        ).onEach { response ->
            when(response) {
                is StoreReadResponse.Data -> {
                    val newItems = response.value ?: emptyList()
                    reduce {
                        val currentList = if (isInitialLoad) emptyList() else state.items
                        val newItemsUnique = if (isInitialLoad) newItems else newItems.filter { newItem -> currentList.none { (it as? UnifiedDisplayItem)?.videoId == newItem.videoId } }

                        state.copy(
                            items = currentList + newItemsUnique,
                            currentOffset = state.currentOffset + newItems.size,
                            endOfList = newItems.size < PAGE_SIZE,
                            isLoadingInitial = false, isLoadingMore = false
                        )
                    }
                }
                is StoreReadResponse.Error -> {
                    reduce { state.copy(isLoadingInitial = false, isLoadingMore = false) }
                    postSideEffect(FullListSideEffect.ShowToast(response.errorMessageOrNull() ?: "Error loading list"))
                }
                else -> {}
            }
        }.launchIn(viewModelScope)
    }

    private fun loadFromLegacyRepository(isInitialLoad: Boolean) = intent {
        val offset = if (isInitialLoad) 0 else state.currentOffset

        val result: Result<Any> = runCatching {
            when (categoryType) {
                MusicCategoryType.TRENDING -> playlistRepository.getHotSongsForCarousel(organization.takeIf { it != "All Vtubers" }).getOrThrow()
                MusicCategoryType.COMMUNITY_PLAYLISTS -> playlistRepository.getOrgPlaylistsPaginated(
                    org = organization, type = "ugp", offset = offset, limit = PAGE_SIZE
                ).getOrThrow()
                MusicCategoryType.ARTIST_RADIOS -> playlistRepository.getOrgPlaylistsPaginated(
                    org = organization, type = "radio", offset = offset, limit = PAGE_SIZE
                ).getOrThrow()
                MusicCategoryType.SYSTEM_PLAYLISTS -> playlistRepository.getOrgPlaylistsPaginated(
                    org = organization, type = "sgp", offset = offset, limit = PAGE_SIZE
                ).getOrThrow()
                MusicCategoryType.DISCOVER_CHANNELS -> playlistRepository.getOrgChannelsPaginated(
                    org = organization, offset = offset, limit = PAGE_SIZE
                ).getOrThrow()
                else -> throw NotImplementedError("Category $categoryType not implemented in Legacy")
            }
        }

        val likedIds = unifiedRepository.observeLikedItemIds().first()
        val downloadedIds = unifiedRepository.observeDownloadedIds().first()

        result.onSuccess { response ->
            val newItems: List<Any> = when (response) {
                is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (response as List<com.example.holodex.data.model.discovery.MusicdexSong>).map { song ->
                        val videoShell = song.toVideoShell()
                        song.toUnifiedDisplayItem(
                            parentVideo = videoShell,
                            isLiked = likedIds.contains("${song.videoId}_${song.start}"),
                            isDownloaded = downloadedIds.contains("${song.videoId}_${song.start}")
                        )
                    }
                }
                is com.example.holodex.data.api.PlaylistListResponse -> response.items
                is com.example.holodex.data.api.PaginatedChannelsResponse -> response.items.map { it.toDiscoveryChannel() }
                else -> emptyList()
            }

            val newItemsCount = newItems.size
            var finalItemsList = if (isInitialLoad) emptyList() else state.items

            if (categoryType == MusicCategoryType.DISCOVER_CHANNELS) {
                val currentChannels = finalItemsList.filterIsInstance<DiscoveryChannel>()
                val incomingChannels = newItems.filterIsInstance<DiscoveryChannel>()
                val allChannels = (currentChannels + incomingChannels).distinctBy { it.id }

                val grouped = allChannels.groupBy { it.suborg?.takeIf { s -> s.isNotBlank() } ?: organization }
                val groupedList = mutableListOf<Any>()
                grouped.keys.sorted().forEach { header ->
                    groupedList.add(SubOrgHeader(header))
                    groupedList.addAll(grouped[header]?.sortedBy { it.name } ?: emptyList())
                }
                finalItemsList = groupedList
            } else {
                finalItemsList = finalItemsList + newItems
            }

            val isEndOfList = newItemsCount < PAGE_SIZE || categoryType == MusicCategoryType.TRENDING

            reduce {
                state.copy(
                    items = finalItemsList,
                    currentOffset = offset + newItemsCount,
                    endOfList = isEndOfList,
                    isLoadingInitial = false,
                    isLoadingMore = false
                )
            }
        }.onFailure { error ->
            Timber.e(error, "Error loading full list")
            postSideEffect(FullListSideEffect.ShowToast("Failed to load data"))
            reduce {
                state.copy(isLoadingInitial = false, isLoadingMore = false)
            }
        }
    }
}

private fun com.example.holodex.data.model.discovery.ChannelDetails.toDiscoveryChannel(): DiscoveryChannel {
    return DiscoveryChannel(
        id = this.id,
        name = this.name,
        englishName = this.englishName,
        photoUrl = this.photoUrl,
        songCount = null,
        suborg = this.group
    )
}