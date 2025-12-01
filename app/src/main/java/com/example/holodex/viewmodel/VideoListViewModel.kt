package com.example.holodex.viewmodel

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.cache.BrowseCacheKey
import com.example.holodex.data.cache.SearchCacheKey
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.SearchHistoryRepository
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.usecase.AddOrFetchAndAddUseCase
import com.example.holodex.playback.player.PlaybackController
import com.example.holodex.util.extractVideoIdFromQuery
import com.example.holodex.viewmodel.autoplay.ContinuationManager
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.state.BrowseFilterState
import com.example.holodex.viewmodel.state.ViewTypePreset
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

// State & SideEffect remain unchanged...
data class VideoListState(
    val browseItems: ImmutableList<UnifiedDisplayItem> = persistentListOf(),
    val browseIsLoadingInitial: Boolean = false,
    val browseIsLoadingMore: Boolean = false,
    val browseIsRefreshing: Boolean = false,
    val browseEndOfList: Boolean = false,
    val browseCurrentOffset: Int = 0,

    val searchItems: ImmutableList<UnifiedDisplayItem> = persistentListOf(),
    val searchIsLoadingInitial: Boolean = false,
    val searchIsLoadingMore: Boolean = false,
    val searchEndOfList: Boolean = false,
    val searchCurrentOffset: Int = 0,

    val activeContextType: MusicCategoryType = MusicCategoryType.LATEST,
    val isSearchActive: Boolean = false,
    val currentSearchQuery: String = "",
    val activeSearchSource: String = "Holodex",
    val browseFilterState: BrowseFilterState,
    val selectedOrganization: String = "Nijisanji",
    val availableOrganizations: List<Pair<String, String?>> = emptyList(),
    val searchHistory: List<String> = emptyList()
)

sealed class VideoListSideEffect {
    data class ShowToast(val message: String) : VideoListSideEffect()
    data class NavigateTo(val destination: VideoListViewModel.NavigationDestination) :
        VideoListSideEffect()
}

@UnstableApi
@HiltViewModel
class VideoListViewModel @Inject constructor(
    private val holodexRepository: HolodexRepository,
    private val unifiedRepository: UnifiedVideoRepository,
    private val sharedPreferences: SharedPreferences,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val continuationManager: ContinuationManager,
    private val playbackController: PlaybackController,
    private val addOrFetchAndAddUseCase: AddOrFetchAndAddUseCase
) : ContainerHost<VideoListState, VideoListSideEffect>, ViewModel() {

    companion object {
        const val TAG = "VideoListViewModel"
        const val PREF_LAST_SELECTED_ORG = "last_selected_org"
        const val PREF_LAST_CATEGORY_TYPE = "last_category_type"
        const val PREF_LAST_SEARCH_QUERY = "last_search_query"
        const val PREF_LAST_BROWSE_FILTERS = "last_browse_filters_v1"
        const val CHANNEL_ID_SEARCH_PREFIX = "channel:"
        private const val PAGE_SIZE = 50
    }

    sealed class NavigationDestination {
        data class VideoDetails(val videoId: String) : NavigationDestination()
        object HomeScreenWithSearch : NavigationDestination()
    }

    var videoItemForDetailScreen: HolodexVideoItem? = null
        private set

    override val container = container<VideoListState, VideoListSideEffect>(
        VideoListState(
            browseFilterState = loadLastBrowseFilters(),
            currentSearchQuery = loadLastSearchQuery(),
            activeContextType = loadLastActiveListContextType(),
            selectedOrganization = sharedPreferences.getString(PREF_LAST_SELECTED_ORG, "Nijisanji")
                ?: "Nijisanji"
        )
    ) {
        intent {
            viewModelScope.launch {
                holodexRepository.availableOrganizations.collect { orgs ->
                    intent { reduce { state.copy(availableOrganizations = orgs) } }
                }
            }
            viewModelScope.launch {
                searchHistoryRepository.loadSearchHistory()
                searchHistoryRepository.searchHistory.collect { history ->
                    intent { reduce { state.copy(searchHistory = history) } }
                }
            }
        }
        initializeAndFetch()
    }

    fun initializeAndFetch() = intent {
        if (state.browseItems.isEmpty() && state.searchItems.isEmpty()) {
            fetchCurrentContextData(isInitial = true, isRefresh = false)
        }
    }

    private fun fetchCurrentContextData(isInitial: Boolean, isRefresh: Boolean) = intent {
        if (state.activeContextType == MusicCategoryType.SEARCH) {
            fetchSearchResultsInternal(isInitial, isRefresh)
        } else {
            fetchBrowseItemsInternal(isInitial, isRefresh)
        }
    }

    private fun fetchBrowseItemsInternal(isInitial: Boolean, isRefresh: Boolean) = intent {
        if (!isRefresh && !isInitial && (state.browseIsLoadingMore || state.browseEndOfList)) return@intent

        reduce {
            state.copy(
                browseIsLoadingInitial = isInitial,
                browseIsLoadingMore = !isInitial && !isRefresh,
                browseIsRefreshing = isRefresh
            )
        }

        val offset = if (isInitial || isRefresh) 0 else state.browseCurrentOffset
        val filters = state.browseFilterState

        val result: Result<List<HolodexVideoItem>> = runCatching {
            if (filters.selectedOrganization == "Favorites") {
                val favChannels = unifiedRepository.getFavoriteChannels().first()
                if (favChannels.isEmpty()) return@runCatching emptyList<HolodexVideoItem>()

                val holodexResults = holodexRepository.getFavoritesFeed(favChannels, filters, offset).getOrNull()?.data ?: emptyList()
                holodexResults
            } else {
                val key = BrowseCacheKey(filters, offset)
                holodexRepository.fetchBrowseList(key, isRefresh).getOrThrow().data
            }
        }

        val likedIds = unifiedRepository.observeLikedItemIds().first()
        val downloadedIds = try { unifiedRepository.getDownloadedIdsSnapshot() } catch (e: Exception) { emptySet() }

        result.onSuccess { rawItems ->
            val unifiedItems =
                rawItems.map { it.toUnifiedDisplayItem(likedIds.contains(it.id), downloadedIds) }
            reduce {
                val currentList = if (isInitial || isRefresh) emptyList() else state.browseItems
                val newItemsUnique =
                    if (isInitial || isRefresh) unifiedItems else unifiedItems.filter { newItem -> currentList.none { it.videoId == newItem.videoId } }

                state.copy(
                    browseItems = (currentList + newItemsUnique).toImmutableList(),
                    browseCurrentOffset = offset + rawItems.size,
                    browseEndOfList = rawItems.isEmpty() || rawItems.size < PAGE_SIZE,
                    browseIsLoadingInitial = false,
                    browseIsLoadingMore = false,
                    browseIsRefreshing = false
                )
            }

            if (unifiedItems.isNotEmpty() && state.activeContextType != MusicCategoryType.SEARCH) {
                continuationManager.setAutoplayContext(unifiedItems)
            }
        }.onFailure {
            postSideEffect(VideoListSideEffect.ShowToast("Failed to load content"))
            reduce {
                state.copy(
                    browseIsLoadingInitial = false,
                    browseIsLoadingMore = false,
                    browseIsRefreshing = false
                )
            }
        }
    }

    private fun fetchSearchResultsInternal(isInitial: Boolean, isRefresh: Boolean) = intent {
        val query = state.currentSearchQuery
        if (query.isBlank()) return@intent

        if (!isRefresh && !isInitial && (state.searchIsLoadingMore || state.searchEndOfList)) return@intent

        reduce {
            state.copy(
                searchIsLoadingInitial = isInitial,
                searchIsLoadingMore = !isInitial && !isRefresh
            )
        }

        val offset = if (isInitial || isRefresh) 0 else state.searchCurrentOffset

        val result = runCatching {
            if (state.activeSearchSource == "My Channels") {
                val channelIds = unifiedRepository.getFavoriteChannelIds().first()
                if (channelIds.isEmpty()) emptyList()
                else holodexRepository.searchMusicOnChannels(query, channelIds).getOrThrow()
            } else {
                val key = SearchCacheKey(query, offset)
                holodexRepository.fetchSearchList(key, isRefresh).getOrThrow().data
            }
        }

        // *** FIX 2: Use Unified Repo for Downloads ***
        val likedIds = unifiedRepository.observeLikedItemIds().first()
        val downloadedIds =
            unifiedRepository.getDownloads().first().map { it.playbackItemId }.toSet()

        result.onSuccess { rawItems ->
            val unifiedItems =
                rawItems.map { it.toUnifiedDisplayItem(likedIds.contains(it.id), downloadedIds) }
            reduce {
                val currentList = if (isInitial || isRefresh) emptyList() else state.searchItems
                state.copy(
                    searchItems = (currentList + unifiedItems).toImmutableList(),
                    searchCurrentOffset = offset + rawItems.size,
                    searchEndOfList = rawItems.isEmpty(),
                    searchIsLoadingInitial = false, searchIsLoadingMore = false
                )
            }
        }.onFailure {
            postSideEffect(VideoListSideEffect.ShowToast("Search failed"))
            reduce { state.copy(searchIsLoadingInitial = false, searchIsLoadingMore = false) }
        }
    }

    // --- PUBLIC ACTIONS ---

    fun refreshCurrentListViaPull() = intent {
        fetchCurrentContextData(isInitial = false, isRefresh = true)
    }

    fun loadMore(contextType: MusicCategoryType) = intent {
        if (contextType == MusicCategoryType.SEARCH) fetchSearchResultsInternal(
            isInitial = false,
            isRefresh = false
        )
        else fetchBrowseItemsInternal(isInitial = false, isRefresh = false)
    }

    fun onVideoClicked(item: UnifiedDisplayItem) = intent {
        videoItemForDetailScreen = null
        postSideEffect(VideoListSideEffect.NavigateTo(NavigationDestination.VideoDetails(item.videoId)))
    }

    fun setSearchActive(isActive: Boolean) = intent {
        reduce { state.copy(isSearchActive = isActive) }
        if (isActive) {
            reduce { state.copy(activeContextType = MusicCategoryType.SEARCH) }
            saveActiveListContextType(MusicCategoryType.SEARCH)
        }
    }

    fun onSearchQueryChange(newQuery: String) = intent {
        reduce { state.copy(currentSearchQuery = newQuery) }
    }

    fun performSearch(query: String) = intent {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            postSideEffect(VideoListSideEffect.ShowToast("Please enter a search term"))
            return@intent
        }

        extractVideoIdFromQuery(trimmed)?.let { videoId ->
            postSideEffect(VideoListSideEffect.NavigateTo(NavigationDestination.VideoDetails(videoId)))
            reduce { state.copy(isSearchActive = false) }
            return@intent
        }

        viewModelScope.launch { searchHistoryRepository.addSearchQueryToHistory(trimmed) }
        saveSearchQuery(trimmed)

        reduce {
            state.copy(
                currentSearchQuery = trimmed,
                isSearchActive = false,
                activeContextType = MusicCategoryType.SEARCH
            )
        }
        fetchSearchResultsInternal(isInitial = true, isRefresh = false)
    }

    fun clearSearchAndReturnToBrowse() = intent {
        reduce {
            state.copy(
                currentSearchQuery = "",
                isSearchActive = false,
                activeContextType = MusicCategoryType.LATEST
            )
        }
        saveSearchQuery("")
        saveActiveListContextType(MusicCategoryType.LATEST)
        fetchBrowseItemsInternal(isInitial = true, isRefresh = false)
    }

    fun setOrganization(orgName: String) = intent {
        if (state.selectedOrganization != orgName) {
            sharedPreferences.edit { putString(PREF_LAST_SELECTED_ORG, orgName) }
            reduce { state.copy(selectedOrganization = orgName) }

            val newFilters = BrowseFilterState.create(
                preset = state.browseFilterState.selectedViewPreset,
                organization = orgName.takeIf { it != "All Vtubers" }
            )
            updateBrowseFilters(newFilters)
        }
    }

    fun updateBrowseFilters(newFilters: BrowseFilterState) = intent {
        if (state.browseFilterState != newFilters) {
            reduce { state.copy(browseFilterState = newFilters) }
            saveBrowseFilters(newFilters)

            if (state.activeContextType == MusicCategoryType.SEARCH) {
                reduce { state.copy(activeContextType = MusicCategoryType.LATEST) }
                saveActiveListContextType(MusicCategoryType.LATEST)
            }
            fetchBrowseItemsInternal(isInitial = true, isRefresh = false)
        }
    }

    fun setBrowseContextAndNavigate(org: String? = null, channelId: String? = null) = intent {
        if (channelId != null) {
            val newQuery = "$CHANNEL_ID_SEARCH_PREFIX$channelId"
            reduce {
                state.copy(
                    currentSearchQuery = newQuery,
                    isSearchActive = false,
                    activeContextType = MusicCategoryType.SEARCH
                )
            }
            saveSearchQuery(newQuery)
            saveActiveListContextType(MusicCategoryType.SEARCH)
            fetchSearchResultsInternal(isInitial = true, isRefresh = false)
            postSideEffect(VideoListSideEffect.NavigateTo(NavigationDestination.HomeScreenWithSearch))
        } else {
            val newFilter = BrowseFilterState.create(
                preset = ViewTypePreset.LATEST_STREAMS,
                organization = org?.takeIf { it != "All Vtubers" },
            )
            updateBrowseFilters(newFilter)
            postSideEffect(VideoListSideEffect.NavigateTo(NavigationDestination.HomeScreenWithSearch))
        }
    }

    fun setActiveSearchSource(source: String) = intent {
        reduce { state.copy(activeSearchSource = source) }
    }

    fun addVideoOrItsSegmentsToQueue(item: PlaybackItem) = intent {
        viewModelScope.launch {
            addOrFetchAndAddUseCase(item)
                .onSuccess { postSideEffect(VideoListSideEffect.ShowToast(it)) }
                .onFailure { postSideEffect(VideoListSideEffect.ShowToast("Failed: ${it.message}")) }
        }
    }

    fun playFavoriteOrLikedSegmentItem(item: PlaybackItem) = intent {
        // We need to construct the list to play.
        // Ideally, we play the list surrounding this item, but for a single item click:
        val itemsToPlay = listOf(item)
        val startIndex = 0

        // Just call it directly. No launch needed.
        playbackController.loadAndPlay(itemsToPlay, startIndex)
    }

    fun clearNavigationRequest() { /* Handled by side effects */
    }

    private fun loadLastBrowseFilters(): BrowseFilterState = try {
        Gson().fromJson(sharedPreferences.getString(PREF_LAST_BROWSE_FILTERS, null), BrowseFilterState::class.java)
            ?: BrowseFilterState.create(ViewTypePreset.UPCOMING_STREAMS)
    } catch (_: Exception) { BrowseFilterState.create(ViewTypePreset.UPCOMING_STREAMS) }

    val browseScreenCategories: List<Pair<String, BrowseFilterState>> get() = listOf(
        "Upcoming & Live Music" to BrowseFilterState.create(ViewTypePreset.UPCOMING_STREAMS),
        "Latest Music" to BrowseFilterState.create(ViewTypePreset.LATEST_STREAMS)
    )

    private fun saveBrowseFilters(filters: BrowseFilterState) =
        sharedPreferences.edit { putString(PREF_LAST_BROWSE_FILTERS, Gson().toJson(filters)) }

    private fun loadLastSearchQuery(): String =
        sharedPreferences.getString(PREF_LAST_SEARCH_QUERY, "") ?: ""

    private fun saveSearchQuery(query: String) =
        sharedPreferences.edit { putString(PREF_LAST_SEARCH_QUERY, query) }

    private fun loadLastActiveListContextType(): MusicCategoryType = try {
        MusicCategoryType.valueOf(
            sharedPreferences.getString(
                PREF_LAST_CATEGORY_TYPE,
                MusicCategoryType.LATEST.name
            )!!
        )
    } catch (_: Exception) {
        MusicCategoryType.LATEST
    }

    private fun saveActiveListContextType(type: MusicCategoryType) =
        sharedPreferences.edit { putString(PREF_LAST_CATEGORY_TYPE, type.name) }

}