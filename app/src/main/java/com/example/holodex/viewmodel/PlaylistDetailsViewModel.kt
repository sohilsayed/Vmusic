package com.example.holodex.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.example.holodex.R
import com.example.holodex.auth.TokenManager
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.data.db.PlaylistItemEntity
import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.data.repository.PlaylistRepository
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.playback.domain.usecase.AddItemsToQueueUseCase
import com.example.holodex.playback.player.PlaybackController
import com.example.holodex.util.ArtworkResolver
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.PaletteExtractor
import com.example.holodex.util.PlaylistFormatter
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toVideoShell // <--- ADDED IMPORT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

data class PlaylistDetailsState(
    val playlist: PlaylistEntity? = null,
    val items: List<UnifiedDisplayItem> = emptyList(),
    val rawItems: List<Any> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isEditMode: Boolean = false,
    val editablePlaylist: PlaylistEntity? = null,
    val editableItems: List<PlaylistItemEntity> = emptyList(),
    val isPlaylistOwned: Boolean = false,
    val isShuffleActive: Boolean = false,
    val dynamicTheme: DynamicTheme = DynamicTheme.default(Color.Black, Color.White)
)

sealed class PlaylistDetailsSideEffect {
    data class ShowToast(val message: String) : PlaylistDetailsSideEffect()
}

@UnstableApi
@HiltViewModel
class PlaylistDetailsViewModel @Inject constructor(
    private val application: Application,
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val unifiedRepository: UnifiedVideoRepository,
    private val addItemsToQueueUseCase: AddItemsToQueueUseCase,
    private val playbackController: PlaybackController,
    private val paletteExtractor: PaletteExtractor,
    private val tokenManager: TokenManager
) : ContainerHost<PlaylistDetailsState, PlaylistDetailsSideEffect>, ViewModel() {

    companion object {
        const val PLAYLIST_ID_ARG = "playlistId"
        const val LIKED_SEGMENTS_PLAYLIST_ID = "-100"
        const val DOWNLOADS_PLAYLIST_ID = "-200"
    }

    val playlistId: String = savedStateHandle.get<String>(PLAYLIST_ID_ARG) ?: ""

    override val container: Container<PlaylistDetailsState, PlaylistDetailsSideEffect> =
        container(PlaylistDetailsState()) {
            if (playlistId.isNotBlank()) {
                loadPlaylistDetails()
            } else {
                intent { reduce { state.copy(isLoading = false, error = "Invalid Playlist ID") } }
            }
        }

    fun loadPlaylistDetails() = intent {
        reduce { state.copy(isLoading = true, error = null) }
        val now = Instant.now().toString()

        try {
            var playlistEntity: PlaylistEntity? = null
            var unifiedItems: List<UnifiedDisplayItem> = emptyList()
            var rawItemsList: List<Any> = emptyList()
            var artworkUrl: String? = null

            val likedIds = unifiedRepository.observeLikedItemIds().first()
            val downloadedIds = unifiedRepository.observeDownloadedIds().first()

            when (playlistId) {
                LIKED_SEGMENTS_PLAYLIST_ID -> {
                    val segments = unifiedRepository.getFavorites().first().filter { it.isSegment }
                    unifiedItems = segments
                    rawItemsList = segments
                    playlistEntity = PlaylistEntity(
                        playlistId = LIKED_SEGMENTS_PLAYLIST_ID.toLong(),
                        name = application.getString(R.string.playlist_title_liked_segments),
                        description = application.getString(R.string.playlist_desc_liked_segments),
                        createdAt = now, last_modified_at = now, serverId = null, owner = null
                    )
                    artworkUrl = segments.firstOrNull()?.artworkUrls?.firstOrNull()
                }

                DOWNLOADS_PLAYLIST_ID -> {
                    val downloads = unifiedRepository.getDownloads().first()
                    unifiedItems = downloads
                    rawItemsList = downloads
                    playlistEntity = PlaylistEntity(
                        playlistId = DOWNLOADS_PLAYLIST_ID.toLong(),
                        name = application.getString(R.string.playlist_title_downloads),
                        description = application.getString(R.string.playlist_desc_downloads),
                        createdAt = now, last_modified_at = now, serverId = null, owner = null
                    )
                    artworkUrl = downloads.firstOrNull()?.artworkUrls?.firstOrNull()
                }

                else -> {
                    val longId = playlistId.toLongOrNull()
                    if (longId != null && longId > 0) {
                        playlistEntity = playlistRepository.getPlaylistById(longId)
                        val playlistItems = playlistRepository.getItemsForPlaylist(longId).first()
                        rawItemsList = playlistItems
                        unifiedItems = playlistItems.map { entity ->
                            entity.toUnifiedDisplayItem(
                                isDownloaded = downloadedIds.contains(entity.itemIdInPlaylist),
                                isLiked = likedIds.contains(entity.itemIdInPlaylist)
                            )
                        }
                        artworkUrl = unifiedItems.firstOrNull()?.artworkUrls?.firstOrNull()
                    } else {
                        val isRadio =
                            playlistId.startsWith(":artist") || playlistId.startsWith(":hot") || playlistId.startsWith(
                                ":radio"
                            )
                        val result =
                            if (isRadio) playlistRepository.getRadioContent(playlistId) else playlistRepository.getFullPlaylistContent(
                                playlistId
                            )
                        val fullPlaylist = result.getOrThrow()

                        val tempStub = PlaylistStub(
                            id = fullPlaylist.id,
                            title = fullPlaylist.title,
                            type = fullPlaylist.type ?: "",
                            description = fullPlaylist.description,
                            artContext = null
                        )
                        val formattedTitle = PlaylistFormatter.getDisplayTitle(
                            tempStub,
                            application
                        ) { en, jp -> jp?.takeIf { it.isNotBlank() } ?: en }
                        val formattedDescription = PlaylistFormatter.getDisplayDescription(
                            tempStub,
                            application
                        ) { en, jp -> jp?.takeIf { it.isNotBlank() } ?: en }

                        playlistEntity = PlaylistEntity(
                            playlistId = 0L,
                            name = formattedTitle,
                            description = formattedDescription,
                            createdAt = fullPlaylist.createdAt,
                            last_modified_at = fullPlaylist.updatedAt,
                            serverId = fullPlaylist.id,
                            owner = null
                        )

                        val apiSongs = fullPlaylist.content ?: emptyList()
                        rawItemsList = apiSongs
                        unifiedItems = apiSongs.map { song ->
                            val videoShell = song.toVideoShell(playlistEntity.name ?: "")
                            val compositeId = "${song.videoId}_${song.start}"
                            song.toUnifiedDisplayItem(
                                parentVideo = videoShell,
                                isLiked = likedIds.contains(compositeId),
                                isDownloaded = downloadedIds.contains(compositeId)
                            )
                        }
                        artworkUrl = ArtworkResolver.getPlaylistArtworkUrl(tempStub)
                            ?: unifiedItems.firstOrNull()?.artworkUrls?.firstOrNull()
                    }
                }
            }

            val theme = paletteExtractor.extractThemeFromUrl(
                artworkUrl,
                DynamicTheme.default(Color.Black, Color.White)
            )
            val isOwned =
                playlistEntity?.owner != null && playlistEntity.owner.toString() == tokenManager.getUserId()

            reduce {
                state.copy(
                    playlist = playlistEntity,
                    items = unifiedItems,
                    rawItems = rawItemsList,
                    isLoading = false,
                    dynamicTheme = theme,
                    isPlaylistOwned = isOwned
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to load playlist details")
            reduce { state.copy(isLoading = false, error = e.localizedMessage ?: "Unknown Error") }
        }
    }

    fun togglePlaylistShuffleMode() = intent {
        reduce { state.copy(isShuffleActive = !state.isShuffleActive) }
    }

    fun playAllItemsInPlaylist() = intent {
        val isRadio = playlistId.startsWith(":")
        if (isRadio) {
            playbackController.loadRadio(playlistId)
        } else {
            if (state.items.isEmpty()) {
                postSideEffect(PlaylistDetailsSideEffect.ShowToast("Playlist is empty"))
                return@intent
            }
            val playbackItems = state.items.map { it.toPlaybackItem() }
            playbackController.loadAndPlay(playbackItems, 0)
            if (state.isShuffleActive) playbackController.toggleShuffle()
        }
    }

    fun playFromItem(tappedItem: UnifiedDisplayItem) = intent {
        if (state.items.isEmpty()) return@intent
        val index = state.items.indexOfFirst { it.playbackItemId == tappedItem.playbackItemId }
            .coerceAtLeast(0)
        val playbackItems = state.items.map { it.toPlaybackItem() }

        playbackController.loadAndPlay(playbackItems, index)
        if (state.isShuffleActive) playbackController.toggleShuffle()
    }

    fun addAllToQueue() = intent {
        val items = state.items.map { it.toPlaybackItem() }
        if (items.isNotEmpty()) {
            addItemsToQueueUseCase(items)
            postSideEffect(PlaylistDetailsSideEffect.ShowToast("Added ${items.size} songs to queue"))
        }
    }

    fun enterEditMode() = intent {
        val editableList = state.rawItems.filterIsInstance<PlaylistItemEntity>()
        if (editableList.isEmpty() && state.rawItems.isNotEmpty()) {
            postSideEffect(PlaylistDetailsSideEffect.ShowToast("Cannot edit this type of playlist"))
            return@intent
        }
        reduce {
            state.copy(
                isEditMode = true,
                editablePlaylist = state.playlist?.copy(),
                editableItems = editableList
            )
        }
    }

    fun cancelEditMode() = intent {
        reduce {
            state.copy(
                isEditMode = false,
                editablePlaylist = null,
                editableItems = emptyList()
            )
        }
    }

    fun updateDraftName(newName: String) = intent {
        reduce { state.copy(editablePlaylist = state.editablePlaylist?.copy(name = newName)) }
    }

    fun updateDraftDescription(desc: String) = intent {
        reduce { state.copy(editablePlaylist = state.editablePlaylist?.copy(description = desc)) }
    }

    fun reorderItemInEditMode(from: Int, to: Int) = intent {
        val list = state.editableItems.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)

        val likedIds = unifiedRepository.observeLikedItemIds().first()
        val downloadedIds = unifiedRepository.observeDownloadedIds().first()
        val unified = list.map {
            it.toUnifiedDisplayItem(
                downloadedIds.contains(it.itemIdInPlaylist),
                likedIds.contains(it.itemIdInPlaylist)
            )
        }

        reduce { state.copy(editableItems = list, items = unified) }
    }

    fun removeItemInEditMode(item: UnifiedDisplayItem) = intent {
        val list = state.editableItems.filterNot { it.itemIdInPlaylist == item.playbackItemId }
        val likedIds = unifiedRepository.observeLikedItemIds().first()
        val downloadedIds = unifiedRepository.observeDownloadedIds().first()
        val unified = list.map {
            it.toUnifiedDisplayItem(
                downloadedIds.contains(it.itemIdInPlaylist),
                likedIds.contains(it.itemIdInPlaylist)
            )
        }
        reduce { state.copy(editableItems = list, items = unified) }
    }

    fun saveChanges() = intent {
        val original = state.playlist
        val draft = state.editablePlaylist
        val draftItems = state.editableItems

        if (draft == null || draft.name.isNullOrBlank()) {
            postSideEffect(PlaylistDetailsSideEffect.ShowToast("Playlist name cannot be empty"))
            return@intent
        }

        val originalSyncedIds =
            state.rawItems.filterIsInstance<PlaylistItemEntity>().filter { !it.isLocalOnly }
                .map { it.itemIdInPlaylist }
        val newSyncedIds = draftItems.filter { !it.isLocalOnly }.map { it.itemIdInPlaylist }

        val contentChanged = originalSyncedIds != newSyncedIds
        val metaChanged = original?.name != draft.name || original?.description != draft.description

        val finalPlaylist = if (contentChanged || metaChanged) {
            draft.copy(syncStatus = SyncStatus.DIRTY, last_modified_at = Instant.now().toString())
        } else {
            draft
        }

        try {
            playlistRepository.savePlaylistEdits(finalPlaylist, draftItems)
            cancelEditMode()
            loadPlaylistDetails()
            postSideEffect(PlaylistDetailsSideEffect.ShowToast("Changes saved"))
        } catch (e: Exception) {
            postSideEffect(PlaylistDetailsSideEffect.ShowToast("Failed to save: ${e.message}"))
        }
    }

    fun clearError() = intent { reduce { state.copy(error = null) } }
}