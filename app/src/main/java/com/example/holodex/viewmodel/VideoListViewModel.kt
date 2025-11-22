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
import com.example.holodex.data.repository.LocalRepository
import com.example.holodex.data.repository.SearchHistoryRepository
import com.example.holodex.playback.PlaybackRequestManager
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.usecase.AddOrFetchAndAddUseCase
import com.example.holodex.util.extractVideoIdFromQuery
import com.example.holodex.viewmodel.autoplay.ContinuationManager
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.state.BrowseFilterState
import com.example.holodex.viewmodel.state.SongSegmentFilterMode
import com.example.holodex.viewmodel.state.ViewTypePreset
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import javax.inject.Inject

// --- State Definitions ---

enum class MusicCategoryType {
    LATEST, UPCOMING_MUSIC, SEARCH, FAVORITES, LIKED_SEGMENTS,
    TRENDING, RECENT_STREAMS, COMMUNITY_PLAYLISTS,
    ARTIST_RADIOS, SYSTEM_PLAYLISTS, DISCOVER_CHANNELS
}

data class VideoListState(
    // Browse Data
    val browseItems: List<UnifiedDisplayItem> = emptyList(),
    val browseIsLoadingInitial: Boolean = false,
    val browseIsLoadingMore: Boolean = false,
    val browseIsRefreshing: Boolean = false,
    val browseEndOfList: Boolean = false,
    val browseCurrentOffset: Int = 0,

    // Search Data
    val searchItems: List<UnifiedDisplayItem> = emptyList(),
    val searchIsLoadingInitial: Boolean = false,
    val searchIsLoadingMore: Boolean = false,
    val searchEndOfList: Boolean = false,
    val searchCurrentOffset: Int = 0,

    // UI Context & Configuration
    val activeContextType: MusicCategoryType = MusicCategoryType.LATEST,
    val isSearchActive: Boolean = false,
    val currentSearchQuery: String = "",
    val activeSearchSource: String = "Holodex",
    val browseFilterState: BrowseFilterState, // Initialized in VM
    val selectedOrganization: String = "Nijisanji",

    // External Data
    val availableOrganizations: List<Pair<String, String?>> = emptyList(),
    val searchHistory: List<String> = emptyList()
)

sealed class VideoListSideEffect {
    data class ShowToast(val message: String) : VideoListSideEffect()
    data class NavigateTo(val destination: VideoListViewModel.NavigationDestination) : VideoListSideEffect()
}

@UnstableApi
@HiltViewModel
class VideoListViewModel @Inject constructor(
    private val holodexRepository: HolodexRepository,
    private val localRepository: LocalRepository,
    private val sharedPreferences: SharedPreferences,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val playbackRequestManager: PlaybackRequestManager,
    private val downloadRepository: DownloadRepository,
    private val continuationManager: ContinuationManager,
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

    // Helper to pass data to details screen (Transient)
    var videoItemForDetailScreen: HolodexVideoItem? = null
        private set

    // Orbit Container
    override val container: Container<VideoListState, VideoListSideEffect> = container(
        VideoListState(
            browseFilterState = loadLastBrowseFilters(),
            currentSearchQuery = loadLastSearchQuery(),
            activeContextType = loadLastActiveListContextType(),
            selectedOrganization = sharedPreferences.getString(PREF_LAST_SELECTED_ORG, "Nijisanji") ?: "Nijisanji"
        )
    ) {
        // Initialize long-running observations
        intent {
            // Observe Organizations
            viewModelScope.launch {
                holodexRepository.availableOrganizations.collect { orgs ->
                    intent { reduce { state.copy(availableOrganizations = orgs) } }
                }
            }
            // Observe Search History
            viewModelScope.launch {
                searchHistoryRepository.loadSearchHistory() // Trigger load
                searchHistoryRepository.searchHistory.collect { history ->
                    intent { reduce { state.copy(searchHistory = history) } }
                }
            }
        }
        // Trigger Initial Fetch
        initializeAndFetch()
    }

    fun initializeAndFetch() = intent {
        if (state.browseItems.isEmpty() && state.searchItems.isEmpty()) {
            fetchCurrentContextData(isInitial = true, isRefresh = false)
        }
    }

    // --- DATA FETCHING LOGIC ---

    private fun fetchCurrentContextData(isInitial: Boolean, isRefresh: Boolean) = intent {
        if (state.activeContextType == MusicCategoryType.SEARCH) {
            fetchSearchResultsInternal(isInitial, isRefresh)
        } else {
            fetchBrowseItemsInternal(isInitial, isRefresh)
        }
    }

    private fun fetchBrowseItemsInternal(isInitial: Boolean, isRefresh: Boolean) = intent {
        if (!isRefresh && !isInitial && (state.browseIsLoadingMore || state.browseEndOfList)) return@intent

        reduce { state.copy(browseIsLoadingInitial = isInitial, browseIsLoadingMore = !isInitial && !isRefresh, browseIsRefreshing = isRefresh) }

        val offset = if (isInitial || isRefresh) 0 else state.browseCurrentOffset
        val filters = state.browseFilterState

        val result = runCatching {
            if (filters.selectedOrganization == "Favorites") {
                // Favorites Logic
                val holodexFavs = holodexRepository.getFavoriteChannelIds().first()
                val externalChannels = localRepository.getAllExternalChannels().first()

                // Simplified: Should be optimized, but functional for now
                val holodexResults = holodexRepository.getFavoritesFeed(holodexFavs, filters, offset).getOrNull()?.data ?: emptyList()

                // We can't easily paginate external channels mixed with API, so we fetch simple recent
                val externalResults = externalChannels.flatMap {
                    holodexRepository.getMusicFromExternalChannel(it.channelId, null).getOrNull()?.data ?: emptyList()
                }

                val combined = (holodexResults + externalResults)
                    .distinctBy { it.id }
                    .sortedByDescending { it.availableAt }

                val start = offset
                val end = (start + PAGE_SIZE).coerceAtMost(combined.size)
                if (start >= combined.size) emptyList() else combined.subList(start, end)
            } else {
                // Standard API Logic
                val key = BrowseCacheKey(filters, offset)
                holodexRepository.fetchBrowseList(key, isRefresh).getOrThrow().data
            }
        }

        val likedIds = holodexRepository.likedItemIds.first()
        val downloadedIds = downloadRepository.getAllDownloads().first().map { it.videoId }.toSet()

        result.onSuccess { rawItems ->
            val unifiedItems = rawItems.map { it.toUnifiedDisplayItem(likedIds.contains(it.id), downloadedIds) }

            reduce {
                val currentList = if (isInitial || isRefresh) emptyList() else state.browseItems
                // Filter dupes if appending
                val newItemsUnique = if (isInitial || isRefresh) unifiedItems else unifiedItems.filter { newItem ->
                    currentList.none { it.videoId == newItem.videoId }
                }

                state.copy(
                    browseItems = currentList + newItemsUnique,
                    browseCurrentOffset = offset + rawItems.size,
                    browseEndOfList = rawItems.isEmpty() || rawItems.size < PAGE_SIZE, // Simple end check
                    browseIsLoadingInitial = false, browseIsLoadingMore = false, browseIsRefreshing = false
                )
            }

            if (unifiedItems.isNotEmpty() && state.activeContextType != MusicCategoryType.SEARCH) {
                continuationManager.setAutoplayContext(unifiedItems)
            }
        }.onFailure {
            Timber.e(it)
            postSideEffect(VideoListSideEffect.ShowToast("Failed to load content"))
            reduce { state.copy(browseIsLoadingInitial = false, browseIsLoadingMore = false, browseIsRefreshing = false) }
        }
    }

    private fun fetchSearchResultsInternal(isInitial: Boolean, isRefresh: Boolean) = intent {
        val query = state.currentSearchQuery
        if (query.isBlank()) return@intent

        if (!isRefresh && !isInitial && (state.searchIsLoadingMore || state.searchEndOfList)) return@intent

        reduce { state.copy(searchIsLoadingInitial = isInitial, searchIsLoadingMore = !isInitial && !isRefresh) }

        val offset = if (isInitial || isRefresh) 0 else state.searchCurrentOffset

        // Smart Dispatch: Check activeSearchSource
        val result = runCatching {
            if (state.activeSearchSource == "My Channels") {
                val channelIds = localRepository.getAllExternalChannels().first().map { it.channelId }
                if (channelIds.isEmpty()) emptyList()
                else holodexRepository.searchMusicOnChannels(query, channelIds).getOrThrow()
            } else {
                val key = SearchCacheKey(query, offset)
                holodexRepository.fetchSearchList(key, isRefresh).getOrThrow().data
            }
        }

        val likedIds = holodexRepository.likedItemIds.first()
        val downloadedIds = downloadRepository.getAllDownloads().first().map { it.videoId }.toSet()

        result.onSuccess { rawItems ->
            val unifiedItems = rawItems.map { it.toUnifiedDisplayItem(likedIds.contains(it.id), downloadedIds) }
            reduce {
                val currentList = if (isInitial || isRefresh) emptyList() else state.searchItems
                state.copy(
                    searchItems = currentList + unifiedItems,
                    searchCurrentOffset = offset + rawItems.size,
                    searchEndOfList = rawItems.isEmpty(), // Search API often doesn't support pagination well, simplistic check
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
        if (contextType == MusicCategoryType.SEARCH) fetchSearchResultsInternal(isInitial = false, isRefresh = false)
        else fetchBrowseItemsInternal(isInitial = false, isRefresh = false)
    }

    fun onVideoClicked(item: UnifiedDisplayItem) = intent {
        // Note: We don't have the raw object easily accessible here since we mapped it.
        // But VideoDetailsViewModel fetches it anyway if needed.
        videoItemForDetailScreen = null // Clear previous
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

        // Save history
        viewModelScope.launch { searchHistoryRepository.addSearchQueryToHistory(trimmed) }
        saveSearchQuery(trimmed)

        reduce { state.copy(currentSearchQuery = trimmed, isSearchActive = false, activeContextType = MusicCategoryType.SEARCH) }
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
                songFilterMode = state.browseFilterState.songSegmentFilterMode,
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
            reduce { state.copy(currentSearchQuery = newQuery, isSearchActive = false, activeContextType = MusicCategoryType.SEARCH) }
            saveSearchQuery(newQuery)
            saveActiveListContextType(MusicCategoryType.SEARCH)
            fetchSearchResultsInternal(isInitial = true, isRefresh = false)
            postSideEffect(VideoListSideEffect.NavigateTo(NavigationDestination.HomeScreenWithSearch))
        } else {
            val newFilter = BrowseFilterState.create(
                preset = ViewTypePreset.LATEST_STREAMS,
                songFilterMode = SongSegmentFilterMode.REQUIRE_SONGS,
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
        viewModelScope.launch {
            playbackRequestManager.submitPlaybackRequest(listOf(item))
        }
    }

    fun clearNavigationRequest() { /* Handled by side effects */ }

    // --- Persistence Helpers ---
    private fun loadLastBrowseFilters(): BrowseFilterState = try {
        Gson().fromJson(sharedPreferences.getString(PREF_LAST_BROWSE_FILTERS, null), BrowseFilterState::class.java)
            ?: BrowseFilterState.create(ViewTypePreset.UPCOMING_STREAMS, SongSegmentFilterMode.ALL)
    } catch (_: Exception) { BrowseFilterState.create(ViewTypePreset.UPCOMING_STREAMS, SongSegmentFilterMode.ALL) }

    private fun saveBrowseFilters(filters: BrowseFilterState) = sharedPreferences.edit { putString(PREF_LAST_BROWSE_FILTERS, Gson().toJson(filters)) }
    private fun loadLastSearchQuery(): String = sharedPreferences.getString(PREF_LAST_SEARCH_QUERY, "") ?: ""
    private fun saveSearchQuery(query: String) = sharedPreferences.edit { putString(PREF_LAST_SEARCH_QUERY, query) }
    private fun loadLastActiveListContextType(): MusicCategoryType = try {
        MusicCategoryType.valueOf(sharedPreferences.getString(PREF_LAST_CATEGORY_TYPE, MusicCategoryType.LATEST.name)!!)
    } catch (_: Exception) { MusicCategoryType.LATEST }
    private fun saveActiveListContextType(type: MusicCategoryType) = sharedPreferences.edit { putString(PREF_LAST_CATEGORY_TYPE, type.name) }

    val browseScreenCategories: List<Pair<String, BrowseFilterState>> get() = listOf(
        "Upcoming & Live Music" to BrowseFilterState.create(ViewTypePreset.UPCOMING_STREAMS, SongSegmentFilterMode.ALL),
        "Latest Streams (with segments)" to BrowseFilterState.create(ViewTypePreset.LATEST_STREAMS, SongSegmentFilterMode.REQUIRE_SONGS),
        "Latest Streams (without segments)" to BrowseFilterState.create(ViewTypePreset.LATEST_STREAMS, SongSegmentFilterMode.EXCLUDE_SONGS)
    )
}