// File: java/com/example/holodex/viewmodel/PlaylistDetailsViewModel.kt
package com.example.holodex.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.holodex.R
import com.example.holodex.auth.TokenManager
import com.example.holodex.data.db.DownloadedItemEntity
import com.example.holodex.data.db.LikedItemEntity
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.data.db.PlaylistItemEntity
import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.model.discovery.MusicdexSong
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.playback.PlaybackRequestManager
import com.example.holodex.playback.domain.repository.PlaybackRepository
import com.example.holodex.playback.domain.usecase.AddItemsToQueueUseCase
import com.example.holodex.util.ArtworkResolver
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.PaletteExtractor
import com.example.holodex.util.PlaylistFormatter
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toVideoShell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class PlaylistDetailsViewModel
@Inject constructor(
    private val application: Application,
    savedStateHandle: SavedStateHandle,
    private val holodexRepository: HolodexRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackRequestManager: PlaybackRequestManager,
    private val playbackRepository: PlaybackRepository,
    private val addItemsToQueueUseCase: AddItemsToQueueUseCase,
    private val paletteExtractor: PaletteExtractor,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        const val PLAYLIST_ID_ARG = "playlistId"
        const val LIKED_SEGMENTS_PLAYLIST_ID = "-100"
        const val DOWNLOADS_PLAYLIST_ID = "-200"
    }

    val playlistId: String = savedStateHandle.get<String>(PLAYLIST_ID_ARG) ?: ""

    private val _playlistDetails = MutableStateFlow<PlaylistEntity?>(null)
    val playlistDetails: StateFlow<PlaylistEntity?> = _playlistDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isPlaylistShuffleActive = MutableStateFlow(false)
    val isPlaylistShuffleActive: StateFlow<Boolean> = _isPlaylistShuffleActive.asStateFlow()

    private val _rawItemsFlow = MutableStateFlow<List<Any>>(emptyList())

    private val _transientMessage = MutableSharedFlow<String>()
    val transientMessage: SharedFlow<String> = _transientMessage.asSharedFlow()

    private val _dynamicTheme = MutableStateFlow(DynamicTheme.default(Color.Black, Color.White))
    val dynamicTheme: StateFlow<DynamicTheme> = _dynamicTheme.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _editablePlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val editablePlaylist: StateFlow<PlaylistEntity?> = _editablePlaylist.asStateFlow()

    private val _editableItems = MutableStateFlow<List<PlaylistItemEntity>>(emptyList())

    val isPlaylistOwned: StateFlow<Boolean> = _playlistDetails.map { playlist ->
        playlist?.owner != null && playlist.owner.toString() == tokenManager.getUserId()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val unifiedPlaylistItems: StateFlow<List<UnifiedDisplayItem>> = combine(
        _isEditMode, _rawItemsFlow, _editableItems,
        holodexRepository.likedItemIds,
        downloadRepository.getAllDownloads().map { list -> list.map { it.videoId }.toSet() }
    ) { isEditing, rawItems, editableItems, likedIds, downloadedIds ->
        val itemsToDisplay = if (isEditing) editableItems else rawItems
        itemsToDisplay.mapNotNull { rawItem ->
            when (rawItem) {
                is PlaylistItemEntity -> rawItem.toUnifiedDisplayItem(
                    isDownloaded = downloadedIds.contains(rawItem.itemIdInPlaylist),
                    isLiked = likedIds.contains(rawItem.itemIdInPlaylist)
                )
                is LikedItemEntity -> rawItem.toUnifiedDisplayItem(
                    isDownloaded = downloadedIds.contains(rawItem.itemId)
                )
                is DownloadedItemEntity -> rawItem.toUnifiedDisplayItem(
                    isLiked = likedIds.contains(rawItem.videoId)
                )
                is MusicdexSong -> {
                    val videoShell = rawItem.toVideoShell(_playlistDetails.value?.name ?: "")
                    rawItem.toUnifiedDisplayItem(
                        parentVideo = videoShell,
                        isLiked = likedIds.contains("${rawItem.videoId}_${rawItem.start}"),
                        isDownloaded = downloadedIds.contains("${rawItem.videoId}_${rawItem.start}")
                    )
                }
                else -> null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun togglePlaylistShuffleMode() {
        _isPlaylistShuffleActive.value = !_isPlaylistShuffleActive.value
    }

    init {
        if (playlistId.isNotBlank()) {
            loadPlaylistDetails()
        } else {
            _isLoading.value = false
            _error.value = "Invalid Playlist ID."
        }
    }

    fun loadPlaylistDetails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val now = Instant.now().toString()
            var artworkUrl: String? = null

            try {
                when (playlistId) {
                    LIKED_SEGMENTS_PLAYLIST_ID -> {
                        val likedItems = holodexRepository.getObservableLikedSongSegments().first()
                        _playlistDetails.value = PlaylistEntity(
                            playlistId = LIKED_SEGMENTS_PLAYLIST_ID.toLong(),
                            name = application.getString(R.string.playlist_title_liked_segments),
                            description = application.getString(R.string.playlist_desc_liked_segments),
                            createdAt = now, last_modified_at = now, serverId = null, owner = null
                        )
                        _rawItemsFlow.value = likedItems
                        artworkUrl = likedItems.firstOrNull()?.artworkUrlSnapshot
                    }

                    DOWNLOADS_PLAYLIST_ID -> {
                        val downloadedItems = downloadRepository.getAllDownloads().first()
                        _playlistDetails.value = PlaylistEntity(
                            playlistId = DOWNLOADS_PLAYLIST_ID.toLong(),
                            name = application.getString(R.string.playlist_title_downloads),
                            description = application.getString(R.string.playlist_desc_downloads),
                            createdAt = now, last_modified_at = now, serverId = null, owner = null
                        )
                        _rawItemsFlow.value = downloadedItems
                        artworkUrl = downloadedItems.firstOrNull()?.artworkUrl
                    }

                    else -> {
                        val longId = playlistId.toLongOrNull()
                        if (longId != null && longId > 0) {
                            val playlist = holodexRepository.getPlaylistById(longId)
                            val items = holodexRepository.getItemsForPlaylist(longId).first()
                            _playlistDetails.value = playlist
                            _rawItemsFlow.value = items
                            artworkUrl = items.firstOrNull()?.songArtworkUrlPlaylist
                        } else {
                            val isRadio = playlistId.startsWith(":artist") || playlistId.startsWith(":hot") || playlistId.startsWith(":radio")
                            val result = if (isRadio) holodexRepository.getRadioContent(playlistId) else holodexRepository.getFullPlaylistContent(playlistId)

                            result.onSuccess { fullPlaylist ->
                                val tempStub = PlaylistStub(
                                    id = fullPlaylist.id, title = fullPlaylist.title, type = fullPlaylist.type ?: "",
                                    description = fullPlaylist.description, artContext = null
                                )
                                val formattedTitle = PlaylistFormatter.getDisplayTitle(tempStub, application) { en, jp -> jp?.takeIf { it.isNotBlank() } ?: en }
                                val formattedDescription = PlaylistFormatter.getDisplayDescription(tempStub, application) { en, jp -> jp?.takeIf { it.isNotBlank() } ?: en }

                                _playlistDetails.value = PlaylistEntity(
                                    playlistId = 0L, name = formattedTitle, description = formattedDescription,
                                    createdAt = fullPlaylist.createdAt, last_modified_at = fullPlaylist.updatedAt,
                                    serverId = fullPlaylist.id, owner = null
                                )
                                _rawItemsFlow.value = fullPlaylist.content ?: emptyList()
                                artworkUrl = ArtworkResolver.getPlaylistArtworkUrl(tempStub) ?: fullPlaylist.content?.firstOrNull()?.artUrl
                            }.onFailure { throw it }
                        }
                    }
                }
                _dynamicTheme.value = paletteExtractor.extractThemeFromUrl(
                    artworkUrl,
                    DynamicTheme.default(Color.Black, Color.White)
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load details for playlist ID: $playlistId")
                _error.value = "Failed to load playlist: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playAllItemsInPlaylist() {
        viewModelScope.launch {
            val isRadio = playlistId.startsWith(":")
            if (isRadio) {
                playbackRepository.prepareAndPlayRadio(playlistId)
            } else {
                val itemsToPlay = unifiedPlaylistItems.value
                if (itemsToPlay.isEmpty()) {
                    _error.value = "Playlist is empty."
                    return@launch
                }
                val isShuffleOn = _isPlaylistShuffleActive.value
                val playbackItems = itemsToPlay.map { it.toPlaybackItem() }
                playbackRequestManager.submitPlaybackRequest(
                    items = playbackItems, startIndex = 0, shouldShuffle = isShuffleOn
                )
            }
        }
    }

    fun addAllToQueue() {
        viewModelScope.launch {
            val items = unifiedPlaylistItems.value
            if (items.isNotEmpty()) {
                val playbackItems = items.map { it.toPlaybackItem() }
                addItemsToQueueUseCase(playbackItems)
                _transientMessage.emit("Added ${items.size} songs to the queue")
            }
        }
    }

    fun playFromItem(tappedItem: UnifiedDisplayItem) {
        viewModelScope.launch {
            val allItems = unifiedPlaylistItems.value
            if (allItems.isEmpty()) return@launch
            val startIndex = allItems.indexOf(tappedItem).coerceAtLeast(0)
            val playbackItems = allItems.map { it.toPlaybackItem() }
            playbackRequestManager.submitPlaybackRequest(
                playbackItems, startIndex, shouldShuffle = _isPlaylistShuffleActive.value
            )
        }
    }

    fun enterEditMode() {
        _playlistDetails.value?.let { originalPlaylist ->
            _editablePlaylist.value = originalPlaylist.copy()
            _editableItems.value = _rawItemsFlow.value.filterIsInstance<PlaylistItemEntity>()
            _isEditMode.value = true
        }
    }

    fun cancelEditMode() {
        _isEditMode.value = false
        _editablePlaylist.value = null
        _editableItems.value = emptyList()
    }

    fun updateDraftName(newName: String) {
        _editablePlaylist.update { it?.copy(name = newName) }
    }

    fun updateDraftDescription(newDescription: String) {
        _editablePlaylist.update { it?.copy(description = newDescription) }
    }

    fun reorderItemInEditMode(from: Int, to: Int) {
        val currentList = _editableItems.value.toMutableList()
        val movedItem = currentList.removeAt(from)
        currentList.add(to, movedItem)
        _editableItems.value = currentList
    }

    fun removeItemInEditMode(itemToRemove: UnifiedDisplayItem) {
        _editableItems.update { currentList ->
            currentList.filterNot { it.itemIdInPlaylist == itemToRemove.playbackItemId }
        }
    }

    fun saveChanges() = viewModelScope.launch {
        val originalPlaylist = _playlistDetails.value ?: return@launch
        val draftPlaylist = _editablePlaylist.value
        val draftItems = _editableItems.value

        if (draftPlaylist == null || draftPlaylist.name.isNullOrBlank()) {
            _error.value = "Playlist name cannot be empty."
            return@launch
        }

        val originalSyncedItemIds = _rawItemsFlow.value.filterIsInstance<PlaylistItemEntity>()
            .filter { !it.isLocalOnly }.map { it.itemIdInPlaylist }
        val newSyncedItemIds = draftItems.filter { !it.isLocalOnly }.map { it.itemIdInPlaylist }

        val contentChanged = originalSyncedItemIds != newSyncedItemIds
        val metadataChanged = originalPlaylist.name != draftPlaylist.name || originalPlaylist.description != draftPlaylist.description

        val finalPlaylistState = if (contentChanged || metadataChanged) {
            draftPlaylist.copy(syncStatus = SyncStatus.DIRTY, last_modified_at = Instant.now().toString())
        } else {
            draftPlaylist
        }

        try {
            holodexRepository.savePlaylistEdits(finalPlaylistState, draftItems)
            cancelEditMode()
            loadPlaylistDetails()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save playlist edits.")
            _error.value = "Failed to save changes: ${e.localizedMessage}"
        }
    }

    fun clearError() {
        _error.value = null
    }

}