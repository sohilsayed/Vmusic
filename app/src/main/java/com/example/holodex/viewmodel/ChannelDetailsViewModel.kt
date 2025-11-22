// File: java/com/example/holodex/viewmodel/ChannelDetailsViewModel.kt
package com.example.holodex.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.data.db.ExternalChannelEntity
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.model.discovery.DiscoveryResponse
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.LocalRepository
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.PaletteExtractor
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toVideoShell
import com.example.holodex.viewmodel.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelDetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val holodexRepository: HolodexRepository,
    private val localRepository: LocalRepository,
    private val paletteExtractor: PaletteExtractor
) : ViewModel() {

    companion object {
        const val CHANNEL_ID_ARG = "channelId"
    }

    val channelId: String = savedStateHandle.get<String>(CHANNEL_ID_ARG) ?: ""

    private val _isExternal = MutableStateFlow(false)
    val isExternal: StateFlow<Boolean> = _isExternal.asStateFlow()

    private val _externalMusicItems = MutableStateFlow<UiState<List<UnifiedDisplayItem>>>(UiState.Loading)
    val externalMusicItems: StateFlow<UiState<List<UnifiedDisplayItem>>> = _externalMusicItems.asStateFlow()

    private val _channelDetailsState = MutableStateFlow<UiState<ChannelDetails>>(UiState.Loading)
    val channelDetailsState: StateFlow<UiState<ChannelDetails>> = _channelDetailsState.asStateFlow()

    private val _dynamicTheme = MutableStateFlow(DynamicTheme.default(Color.Black, Color.White))
    val dynamicTheme: StateFlow<DynamicTheme> = _dynamicTheme.asStateFlow()

    private val _discoveryState = MutableStateFlow<UiState<DiscoveryResponse>>(UiState.Loading)
    val discoveryState: StateFlow<UiState<DiscoveryResponse>> = _discoveryState.asStateFlow()

    private val _popularSongsState = MutableStateFlow<UiState<List<UnifiedDisplayItem>>>(UiState.Loading)
    val popularSongsState: StateFlow<UiState<List<UnifiedDisplayItem>>> = _popularSongsState.asStateFlow()

    init {
        viewModelScope.launch {
            // Look up the channel in our local repository first
            val externalChannel = localRepository.getExternalChannel(channelId)

            if (externalChannel != null) {
                // It's an external channel
                _isExternal.value = true
                fetchChannelDetailsFromExternal(externalChannel)
                loadInitialPageFromExternalSource()
            } else {
                // It's a Holodex channel
                _isExternal.value = false
                loadAllHolodexContent()
            }
        }
    }

    private fun loadAllHolodexContent() {
        viewModelScope.launch {
            launch { fetchChannelDetails() }
            launch { fetchChannelDiscovery() }
            launch { fetchPopularSongs() }
        }
    }

    private fun fetchChannelDetailsFromExternal(channel: ExternalChannelEntity) {
        val details = ChannelDetails(
            id = channel.channelId,
            name = channel.name,
            englishName = channel.name,
            description = "Music from this channel is sourced directly from YouTube.",
            photoUrl = channel.photoUrl,
            bannerUrl = null, org = "External", suborg = null, twitter = null, group = null
        )
        _channelDetailsState.value = UiState.Success(details)
        viewModelScope.launch {
            _dynamicTheme.value = paletteExtractor.extractThemeFromUrl(
                channel.photoUrl,
                DynamicTheme.default(Color.Black, Color.White)
            )
        }
    }

    private fun loadInitialPageFromExternalSource() {
        viewModelScope.launch {
            _externalMusicItems.value = UiState.Loading
            holodexRepository.getMusicFromExternalChannel(channelId, null)
                .onSuccess { result ->
                    val unifiedItems = result.data.map { it.toUnifiedDisplayItem(isLiked = false, downloadedSegmentIds = emptySet()) }
                    _externalMusicItems.value = UiState.Success(unifiedItems)
                }.onFailure { _externalMusicItems.value = UiState.Error(it.localizedMessage ?: "Failed to load music.") }
        }
    }

    private suspend fun fetchChannelDetails() {
        holodexRepository.getChannelDetails(channelId)
            .onSuccess {
                _channelDetailsState.value = UiState.Success(it)
                _dynamicTheme.value = paletteExtractor.extractThemeFromUrl(
                    it.bannerUrl,
                    DynamicTheme.default(Color.Black, Color.White)
                )
            }
            .onFailure { _channelDetailsState.value = UiState.Error(it.localizedMessage ?: "Failed to load channel details") }
    }

    private suspend fun fetchChannelDiscovery() {
        holodexRepository.getDiscoveryForChannel(channelId)
            .onSuccess { _discoveryState.value = UiState.Success(it) }
            .onFailure { _discoveryState.value = UiState.Error(it.localizedMessage ?: "Failed to load discovery content") }
    }

    private suspend fun fetchPopularSongs() {
        holodexRepository.getHotSongsForCarousel(channelId = channelId)
            .onSuccess { songs ->
                val displayItems = songs.map { song ->
                    val videoShell = song.toVideoShell()
                    song.toUnifiedDisplayItem(
                        parentVideo = videoShell,
                        isLiked = false,
                        isDownloaded = false
                    )
                }
                _popularSongsState.value = UiState.Success(displayItems)
            }
            .onFailure { _popularSongsState.value = UiState.Error(it.localizedMessage ?: "Failed to load popular songs") }
    }
}