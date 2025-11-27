// File: java/com/example/holodex/viewmodel/DiscoveryViewModel.kt

package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.auth.AuthState
import com.example.holodex.data.model.discovery.DiscoveryResponse
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.playback.PlaybackRequestManager
import com.example.holodex.playback.domain.repository.PlaybackRepository
import com.example.holodex.playback.domain.usecase.AddItemsToQueueUseCase
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toVideoShell
import com.example.holodex.viewmodel.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toVideoShell
import com.example.holodex.viewmodel.mappers.toPlaybackItem

enum class ShelfType {
    RECENT_STREAMS,
    SYSTEM_PLAYLISTS,
    ARTIST_RADIOS,
    FAN_PLAYLISTS,
    TRENDING_SONGS,
    DISCOVER_CHANNELS,
    FOR_YOU
}

data class DiscoveryScreenState(
    val shelves: Map<ShelfType, UiState<List<Any>>> = emptyMap(),
    val shelfOrder: List<ShelfType> = emptyList()
)

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val holodexRepository: HolodexRepository,
    private val playbackRequestManager: PlaybackRequestManager,
    private val playbackRepository: PlaybackRepository,
    private val addItemsToQueueUseCase: AddItemsToQueueUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryScreenState())
    val uiState: StateFlow<DiscoveryScreenState> = _uiState.asStateFlow()

    private val _forYouState = MutableStateFlow<UiState<DiscoveryResponse>>(UiState.Loading)
    val forYouState: StateFlow<UiState<DiscoveryResponse>> = _forYouState.asStateFlow()

    private val _transientMessage = MutableSharedFlow<String>()
    val transientMessage: SharedFlow<String> = _transientMessage.asSharedFlow()

    fun loadDiscoveryContent(organization: String, authState: AuthState) {
        val isFavoritesView = organization == "Favorites"
        val newShelfOrder = if (isFavoritesView && authState is AuthState.LoggedIn) {
            listOf(ShelfType.FOR_YOU)
        } else {
            listOf(
                ShelfType.RECENT_STREAMS,
                ShelfType.SYSTEM_PLAYLISTS,
                ShelfType.ARTIST_RADIOS,
                ShelfType.FAN_PLAYLISTS,
                ShelfType.TRENDING_SONGS,
                ShelfType.DISCOVER_CHANNELS
            )
        }

        val initialShelves = newShelfOrder.associateWith { UiState.Loading }
        _uiState.value = DiscoveryScreenState(shelves = initialShelves, shelfOrder = newShelfOrder)

        if (isFavoritesView && authState is AuthState.LoggedIn) {
            fetchFavoritesHub()
        } else {
            val orgParam = organization.takeIf { it != "All Vtubers" }
            fetchTrendingSongs(orgParam)
            fetchDiscoveryHub(organization)
        }
    }

    fun loadForYouContent() {
        _forYouState.value = UiState.Loading
        viewModelScope.launch {
            holodexRepository.getFavoritesHubContent()
                .onSuccess { response ->
                    _forYouState.value = UiState.Success(response)
                }
                .onFailure { error ->
                    _forYouState.value = UiState.Error(error.localizedMessage ?: "Failed to load your content")
                }
        }
    }

    private fun fetchTrendingSongs(organization: String?) {
        viewModelScope.launch {
            holodexRepository.getHotSongsForCarousel(org = organization)
                .onSuccess { songs ->
                    val displayItems = songs.map { song ->
                        val videoShell = song.toVideoShell()
                        song.toUnifiedDisplayItem(                        // FIX: Updated signature

                            parentVideo = videoShell,
                            isLiked = false, // We don't check likes for trending carousel for performance
                            isDownloaded = false
                        )
                    }
                    _uiState.update { s -> s.copy(shelves = s.shelves + (ShelfType.TRENDING_SONGS to UiState.Success(displayItems))) }
                }.onFailure { e ->
                    _uiState.update { s -> s.copy(shelves = s.shelves + (ShelfType.TRENDING_SONGS to UiState.Error(e.localizedMessage ?: "Error"))) }
                }
        }
    }

    private fun fetchDiscoveryHub(org: String) {
        viewModelScope.launch {
            holodexRepository.getDiscoveryHubContent(org)
                .onSuccess { response ->
                    val allPlaylists = response.recommended?.playlists ?: emptyList()

                    val systemPlaylists = allPlaylists.filter { it.type.startsWith("playlist/") }
                    val radios = allPlaylists.filter { it.type.startsWith("radio/") }
                    val communityPlaylists = allPlaylists.filter { it.type == "ugp" }
                    val recentStreams = response.recentSingingStreams?.filter {
                        it.playlist?.content?.isNotEmpty() == true
                    } ?: emptyList()
                    val discoverChannels = response.channels ?: emptyList()

                    _uiState.update { s ->
                        s.copy(
                            shelves = s.shelves +
                                    (ShelfType.RECENT_STREAMS to UiState.Success(recentStreams)) +
                                    (ShelfType.SYSTEM_PLAYLISTS to UiState.Success(systemPlaylists)) +
                                    (ShelfType.ARTIST_RADIOS to UiState.Success(radios)) +
                                    (ShelfType.FAN_PLAYLISTS to UiState.Success(communityPlaylists)) +
                                    (ShelfType.DISCOVER_CHANNELS to UiState.Success(discoverChannels))
                        )
                    }
                }.onFailure { e ->
                    val errorState = UiState.Error(e.localizedMessage ?: "Error")
                    _uiState.update { s ->
                        s.copy(
                            shelves = s.shelves +
                                    (ShelfType.RECENT_STREAMS to errorState) +
                                    (ShelfType.SYSTEM_PLAYLISTS to errorState) +
                                    (ShelfType.ARTIST_RADIOS to errorState) +
                                    (ShelfType.FAN_PLAYLISTS to errorState) +
                                    (ShelfType.DISCOVER_CHANNELS to errorState)
                        )
                    }
                }
        }
    }

    private fun fetchFavoritesHub() {
        viewModelScope.launch {
            holodexRepository.getFavoritesHubContent()
                .onSuccess { response ->
                    val recentStreams = response.recentSingingStreams?.filter {
                        it.playlist?.content?.isNotEmpty() == true
                    } ?: emptyList()
                    _uiState.update { s -> s.copy(shelves = s.shelves + (ShelfType.FOR_YOU to UiState.Success(recentStreams))) }
                }.onFailure { e ->
                    _uiState.update { s -> s.copy(shelves = s.shelves + (ShelfType.FOR_YOU to UiState.Error(e.localizedMessage ?: "Error"))) }
                }
        }
    }

    fun playUnifiedItem(item: UnifiedDisplayItem) {
        viewModelScope.launch {
            playbackRequestManager.submitPlaybackRequest(items = listOf(item.toPlaybackItem()))
        }
    }
    fun playRadioPlaylist(playlist: PlaylistStub) {
        viewModelScope.launch {
            if (playlist.type.startsWith("radio")) {
                Timber.d("Playing playlist as Radio: ${playlist.id}")
                playbackRepository.prepareAndPlayRadio(playlist.id)
            } else {
                // This is a fallback for playing a normal playlist from this screen
                val result = holodexRepository.getFullPlaylistContent(playlist.id)
                result.onSuccess { fullPlaylist ->
                    val playbackItems = fullPlaylist.content?.mapNotNull { song ->
                        if (song.channel.id == null) {
                            null
                        } else {
                            val videoShell = song.toVideoShell(fullPlaylist.title)
                            song.toPlaybackItem(videoShell)
                        }
                    } ?: emptyList()

                    if (playbackItems.isNotEmpty()) {
                        playbackRequestManager.submitPlaybackRequest(items = playbackItems)
                    } else {
                        _transientMessage.emit("This playlist appears to be empty.")
                    }
                }.onFailure { error ->
                    _transientMessage.emit("Error: Could not load playlist.")
                }
            }
        }
    }
    fun addAllToQueue(items: List<UnifiedDisplayItem>) {
        viewModelScope.launch {
            if (items.isNotEmpty()) {
                val playbackItems = items.map { it.toPlaybackItem() }
                addItemsToQueueUseCase(playbackItems)
                _transientMessage.emit("Added ${items.size} songs to queue.")
            }
        }
    }
}