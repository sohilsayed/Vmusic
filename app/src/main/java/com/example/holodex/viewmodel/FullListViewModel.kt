package com.example.holodex.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.model.discovery.DiscoveryChannel
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toVideoShell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

// --- STATE & SIDE EFFECT DEFINITIONS ---

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

// ---------------------------------------

@UnstableApi
@HiltViewModel
class FullListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val holodexRepository: HolodexRepository,
    private val unifiedRepository: UnifiedVideoRepository,
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

    override val container: Container<FullListState, FullListSideEffect> = container(FullListState()) {
        loadMore(isInitialLoad = true)
    }

    fun loadMore(isInitialLoad: Boolean = false) = intent {
        if (!isInitialLoad && (state.isLoadingMore || state.endOfList)) return@intent

        reduce {
            state.copy(
                isLoadingInitial = isInitialLoad,
                isLoadingMore = !isInitialLoad
            )
        }

        val offset = if (isInitialLoad) 0 else state.currentOffset

        val result: Result<Any> = runCatching {
            when (categoryType) {
                MusicCategoryType.TRENDING -> holodexRepository.getHotSongsForCarousel(organization.takeIf { it != "All Vtubers" }).getOrThrow()
                MusicCategoryType.UPCOMING_MUSIC -> holodexRepository.getUpcomingMusicPaginated(
                    org = organization.takeIf { it != "All Vtubers" },
                    offset = offset
                ).getOrThrow()
                MusicCategoryType.RECENT_STREAMS -> holodexRepository.getLatestSongsPaginated(offset = offset, limit = PAGE_SIZE).getOrThrow()
                MusicCategoryType.COMMUNITY_PLAYLISTS -> holodexRepository.getOrgPlaylistsPaginated(
                    org = organization, type = "ugp", offset = offset, limit = PAGE_SIZE
                ).getOrThrow()
                MusicCategoryType.ARTIST_RADIOS -> holodexRepository.getOrgPlaylistsPaginated(
                    org = organization, type = "radio", offset = offset, limit = PAGE_SIZE
                ).getOrThrow()
                MusicCategoryType.SYSTEM_PLAYLISTS -> holodexRepository.getOrgPlaylistsPaginated(
                    org = organization, type = "sgp", offset = offset, limit = PAGE_SIZE
                ).getOrThrow()
                MusicCategoryType.DISCOVER_CHANNELS -> holodexRepository.getOrgChannelsPaginated(
                    org = organization, offset = offset, limit = PAGE_SIZE
                ).getOrThrow()
                else -> throw NotImplementedError("Category $categoryType not implemented")
            }
        }

        result.onSuccess { response ->
            val likedIds = holodexRepository.likedItemIds.first()

            // FIX: Use Unified Repository for downloads
            val downloadedIds = unifiedRepository.getDownloads().first().map { it.playbackItemId }.toSet()

            val newItems: List<Any> = when (response) {
                is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (response as List<com.example.holodex.data.model.discovery.MusicdexSong>).map { song ->
                        // FIX: Ensure toVideoShell is imported
                        val videoShell = song.toVideoShell()
                        song.toUnifiedDisplayItem(
                            parentVideo = videoShell,
                            isLiked = likedIds.contains("${song.videoId}_${song.start}"),
                            isDownloaded = downloadedIds.contains("${song.videoId}_${song.start}")
                        )
                    }
                }
                is com.example.holodex.data.cache.FetcherResult<*> -> {
                    (response.data as List<com.example.holodex.data.model.HolodexVideoItem>).map { video ->
                        video.toUnifiedDisplayItem(
                            isLiked = likedIds.contains(video.id),
                            downloadedSegmentIds = downloadedIds
                        )
                    }
                }
                is com.example.holodex.data.api.PaginatedSongsResponse -> {
                    response.items.map { song ->
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