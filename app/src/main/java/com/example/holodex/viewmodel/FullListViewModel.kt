// File: java/com/example/holodex/viewmodel/FullListViewModel.kt

package com.example.holodex.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.model.discovery.DiscoveryChannel
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.viewmodel.VideoListViewModel.ListStateHolder
import com.example.holodex.viewmodel.VideoListViewModel.MusicCategoryType
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toVideoShell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject


@UnstableApi
@HiltViewModel
class FullListViewModel
@Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val holodexRepository: HolodexRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    companion object {
        const val CATEGORY_TYPE_ARG = "category"
        const val ORG_ARG = "org"
        private const val TAG = "FullListViewModel"
        private const val PAGE_SIZE = 50
    }

    val categoryType: MusicCategoryType = MusicCategoryType.valueOf(
        savedStateHandle.get<String>(CATEGORY_TYPE_ARG) ?: MusicCategoryType.TRENDING.name
    )
    private val organization: String = URLDecoder.decode(
        savedStateHandle.get<String>(ORG_ARG) ?: "All Vtubers",
        StandardCharsets.UTF_8.toString()
    )

    val listState = ListStateHolder<Any>()

    init {
        Timber.d("$TAG: Initialized for category: $categoryType, org: $organization")
        loadMore(isInitialLoad = true)
    }

    fun loadMore(isInitialLoad: Boolean = false) {
        if (listState.isLoadingMore.value || listState.endOfList.value) return

        listState.job?.cancel()
        listState.job = viewModelScope.launch {
            if (isInitialLoad) {
                listState.isLoadingInitial.value = true
                listState.currentOffset = 0
                listState.items.value = emptyList()
            } else {
                listState.isLoadingMore.value = true
            }

            val result: Result<Any> = when (categoryType) {
                MusicCategoryType.TRENDING -> holodexRepository.getHotSongsForCarousel(organization.takeIf { it != "All Vtubers" })

                MusicCategoryType.UPCOMING_MUSIC -> holodexRepository.getUpcomingMusicPaginated(
                    org = organization.takeIf { it != "All Vtubers" },
                    offset = listState.currentOffset
                )
                MusicCategoryType.RECENT_STREAMS -> holodexRepository.getLatestSongsPaginated(offset = listState.currentOffset, limit = PAGE_SIZE)

                // --- START OF IMPLEMENTATION ---
                MusicCategoryType.COMMUNITY_PLAYLISTS -> holodexRepository.getOrgPlaylistsPaginated(
                    org = organization,
                    type = "ugp", // User Generated Playlist
                    offset = listState.currentOffset,
                    limit = PAGE_SIZE
                )
                MusicCategoryType.ARTIST_RADIOS -> holodexRepository.getOrgPlaylistsPaginated(
                    org = organization,
                    type = "radio",
                    offset = listState.currentOffset,
                    limit = PAGE_SIZE
                )
                MusicCategoryType.SYSTEM_PLAYLISTS -> holodexRepository.getOrgPlaylistsPaginated(
                    org = organization,
                    type = "sgp", // System Generated Playlist
                    offset = listState.currentOffset,
                    limit = PAGE_SIZE
                )
                // --- END OF IMPLEMENTATION ---

                MusicCategoryType.DISCOVER_CHANNELS -> holodexRepository.getOrgChannelsPaginated(
                    org = organization,
                    offset = listState.currentOffset,
                    limit = PAGE_SIZE
                )

                else -> Result.failure(NotImplementedError("Category $categoryType not implemented for FullListView."))
            }


            result.onSuccess { response ->
                val likedIds = holodexRepository.likedItemIds.first()
                val downloadedIds = downloadRepository.getAllDownloads().first().map { it.videoId }.toSet()

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

                    is com.example.holodex.data.api.PlaylistListResponse -> {
                        response.items
                    }

                    is com.example.holodex.data.api.PaginatedChannelsResponse -> {
                        response.items.map { it.toDiscoveryChannel() }
                    }

                    else -> emptyList()
                }

                if (categoryType == MusicCategoryType.DISCOVER_CHANNELS) {
                    val currentChannels = if (isInitialLoad) {
                        emptyList()
                    } else {
                        listState.items.value.filterIsInstance<DiscoveryChannel>()
                    }

                    val newChannels = newItems.filterIsInstance<DiscoveryChannel>()

                    val allChannels = (currentChannels + newChannels).distinctBy { it.id }

                    val groupedChannels = allChannels.groupBy { channel ->
                        channel.suborg?.takeIf { it.isNotBlank() } ?: organization
                    }

                    val flattenedList = mutableListOf<Any>()
                    groupedChannels.keys.sorted().forEach { subOrgName ->
                        flattenedList.add(SubOrgHeader(name = subOrgName))
                        val channelsInGroup = groupedChannels[subOrgName]
                        if (channelsInGroup != null) {
                            flattenedList.addAll(channelsInGroup.sortedBy { it.name })
                        }
                    }
                    listState.items.value = flattenedList
                } else {
                    listState.items.value += newItems
                }

                val newItemsCount = when (response) {
                    is List<*> -> response.size
                    is com.example.holodex.data.api.PaginatedSongsResponse -> response.items.size
                    is com.example.holodex.data.api.PlaylistListResponse -> response.items.size
                    is com.example.holodex.data.api.PaginatedChannelsResponse -> response.items.size
                    else -> 0
                }
                listState.currentOffset += newItemsCount

                val totalAvailable = when (response) {
                    is com.example.holodex.data.api.PaginatedSongsResponse -> response.getTotalAsInt()
                    is com.example.holodex.data.api.PlaylistListResponse -> response.total
                    is com.example.holodex.data.api.PaginatedChannelsResponse -> response.getTotalAsInt()
                    else -> null
                }

                if (newItemsCount < PAGE_SIZE || newItemsCount == 0 || (totalAvailable != null && listState.items.value.filter { it !is SubOrgHeader }.size >= totalAvailable)) {
                    listState.endOfList.value = true
                }
                if (categoryType == MusicCategoryType.TRENDING) {
                    listState.endOfList.value = true
                }

            }.onFailure { Timber.e(it) }

            if (isInitialLoad) listState.isLoadingInitial.value =
                false else listState.isLoadingMore.value = false
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