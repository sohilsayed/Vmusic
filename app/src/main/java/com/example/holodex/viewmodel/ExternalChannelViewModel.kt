// File: java/com/example/holodex/viewmodel/ExternalChannelViewModel.kt
package com.example.holodex.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.data.db.ExternalChannelEntity
import com.example.holodex.data.model.ChannelSearchResult
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.LocalRepository
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.PaletteExtractor
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ExternalChannelViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val holodexRepository: HolodexRepository,
    private val localRepository: LocalRepository,
    private val paletteExtractor: PaletteExtractor
) : ViewModel() {

    val channelId: String = savedStateHandle.get<String>("channelId") ?: ""

    // States for the "Add Channel" dialog
    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchState = MutableStateFlow<UiState<List<ChannelSearchResult>>>(UiState.Success(emptyList()))
    val searchState: StateFlow<UiState<List<ChannelSearchResult>>> = _searchState.asStateFlow()
    private val _isAdding = MutableStateFlow<Set<String>>(emptySet())
    val isAdding: StateFlow<Set<String>> = _isAdding.asStateFlow()

    // States for the "External Channel Details" screen
    private val _channelDetails = MutableStateFlow<ChannelDetails?>(null)
    val channelDetails: StateFlow<ChannelDetails?> = _channelDetails.asStateFlow()

    private val _musicItems = MutableStateFlow<List<UnifiedDisplayItem>>(emptyList())
    val musicItems: StateFlow<List<UnifiedDisplayItem>> = _musicItems.asStateFlow()

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    val dynamicTheme: StateFlow<DynamicTheme> get() = _dynamicTheme
    private val _dynamicTheme = MutableStateFlow(DynamicTheme.default(Color.Black, Color.White))

    // --- PAGINATION STATE - NOW PUBLIC ---
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _endOfList = MutableStateFlow(false)
    val endOfList: StateFlow<Boolean> = _endOfList.asStateFlow()

    private var nextPageCursor: Page? = null

    init {
        if (channelId.isNotBlank()) {
            loadInitialDataForScreen()
        }
        viewModelScope.launch {
            _searchQuery.debounce(500).collect { query ->
                if (query.length > 2) {
                    performChannelSearch(query)
                } else if (query.isEmpty()) {
                    _searchState.value = UiState.Success(emptyList())
                }
            }
        }
    }

    private fun loadInitialDataForScreen() {
        viewModelScope.launch {
            val channel = localRepository.getExternalChannel(channelId)
            if (channel != null) {
                _channelDetails.value = ChannelDetails(
                    id = channel.channelId, name = channel.name, englishName = channel.name,
                    description = "Music from this channel is sourced directly from YouTube.",
                    photoUrl = channel.photoUrl, bannerUrl = null, org = "External",
                    suborg = null, twitter = null, group = null
                )
                _dynamicTheme.value = paletteExtractor.extractThemeFromUrl(
                    channel.photoUrl, DynamicTheme.default(Color.Black, Color.White)
                )
                loadMoreMusic(isInitialLoad = true)
            } else {
                _uiState.value = UiState.Error("Channel not found in local library.")
            }
        }
    }

    fun loadMoreMusic(isInitialLoad: Boolean = false) {
        if (_isLoadingMore.value || (_endOfList.value && !isInitialLoad)) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            if (isInitialLoad) {
                _uiState.value = UiState.Loading
                _musicItems.value = emptyList()
                nextPageCursor = null
                _endOfList.value = false
            }

            holodexRepository.getMusicFromExternalChannel(channelId, nextPageCursor)
                .onSuccess { result ->
                    val newItems = result.data.map { it.toUnifiedDisplayItem(isLiked = false, downloadedSegmentIds = emptySet()) }
                    _musicItems.value += newItems
                    nextPageCursor = result.nextPageCursor as? Page
                    if (nextPageCursor == null) {
                        _endOfList.value = true
                    }
                    _uiState.value = UiState.Success(Unit)
                }.onFailure {
                    _uiState.value = UiState.Error(it.localizedMessage ?: "Failed to load music.")
                }
            _isLoadingMore.value = false
        }
    }

    // --- Logic for "Add Channel" Dialog ---
    fun openDialog() { _showDialog.value = true }
    fun closeDialog() {
        _showDialog.value = false
        _searchQuery.value = ""
        _searchState.value = UiState.Success(emptyList())
    }
    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    private fun performChannelSearch(query: String) {
        viewModelScope.launch {
            _searchState.value = UiState.Loading
            holodexRepository.searchForExternalChannels(query)
                .onSuccess { results -> _searchState.value = UiState.Success(results) }
                .onFailure { error -> _searchState.value = UiState.Error(error.localizedMessage ?: "Search failed") }
        }
    }
    fun addChannel(channel: ChannelSearchResult) {
        viewModelScope.launch {
            _isAdding.value += channel.channelId
            val entity = ExternalChannelEntity(
                channelId = channel.channelId, name = channel.name, photoUrl = channel.thumbnailUrl
            )
            localRepository.addExternalChannel(entity)
        }
    }
}