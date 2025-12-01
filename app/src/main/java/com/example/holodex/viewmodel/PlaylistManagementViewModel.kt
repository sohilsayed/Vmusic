package com.example.holodex.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.holodex.R
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.data.db.PlaylistItemEntity
import com.example.holodex.data.db.StarredPlaylistEntity
import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.util.PlaylistFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import kotlin.math.absoluteValue

data class PendingPlaylistItemDetails(
    val videoId: String,
    val itemType: LikedItemType,
    val titleForDisplay: String,
    val artistForDisplay: String,
    val artworkForDisplay: String?,
    val songStartSeconds: Int? = null,
    val songEndSeconds: Int? = null,
    val isExternal: Boolean
)

@OptIn(UnstableApi::class)
@HiltViewModel
class PlaylistManagementViewModel @Inject constructor(
    private val application: Application,
    private val holodexRepository: HolodexRepository,
    private val unifiedRepository: UnifiedVideoRepository
) : ViewModel() {

    companion object {
        const val TAG = "PlaylistMgmtVM"
        const val LIKED_SEGMENTS_PLAYLIST_ID = -100L
        const val DOWNLOADS_PLAYLIST_ID = -200L
    }

    private val userCreatedPlaylists: Flow<List<PlaylistEntity>> = holodexRepository.getAllPlaylists()
    private val starredPlaylists: Flow<List<StarredPlaylistEntity>> = holodexRepository.getStarredPlaylistsFlow()

    // Migration: Use Unified Display Items instead of legacy DownloadedItemEntity
    private val downloadsFlow: Flow<List<UnifiedDisplayItem>> = unifiedRepository.getDownloads()

    val allDisplayablePlaylists: StateFlow<List<PlaylistEntity>> =
        combine(
            userCreatedPlaylists,
            starredPlaylists,
            downloadsFlow,
        ) { userPlaylists, starred, downloads ->
            val syntheticPlaylists = mutableListOf<PlaylistEntity>()
            val now = Instant.now().toString()

            // 1. Synthetic "Liked Segments" Playlist
            syntheticPlaylists.add(
                PlaylistEntity(
                    playlistId = LIKED_SEGMENTS_PLAYLIST_ID,
                    name = application.getString(R.string.playlist_title_liked_segments),
                    description = application.getString(R.string.playlist_desc_liked_segments),
                    createdAt = now, last_modified_at = now, serverId = null, owner = null
                )
            )

            // 2. Synthetic "Downloads" Playlist (Only if downloads exist)
            if (downloads.any { it.isDownloaded }) {
                syntheticPlaylists.add(
                    PlaylistEntity(
                        playlistId = DOWNLOADS_PLAYLIST_ID,
                        name = application.getString(R.string.playlist_title_downloads),
                        description = application.getString(R.string.playlist_desc_downloads),
                        createdAt = now, last_modified_at = now, serverId = null, owner = null
                    )
                )
            }

            // 3. Starred Playlists (Merged with User Playlists)
            val starredAsDisplayable = starred.map { starredItem ->
                val userPlaylistMatch = userPlaylists.find { it.serverId == starredItem.playlistId }
                if (userPlaylistMatch != null) {
                    userPlaylistMatch
                } else {
                    // Generate a temporary negative ID for display stability
                    val uniqueNegativeId = ("starred_${starredItem.playlistId}".hashCode()).absoluteValue * -1L
                    val tempStub = PlaylistStub(
                        id = starredItem.playlistId,
                        title = "Starred Playlist", type = "", artContext = null,
                        description = "ID: ${starredItem.playlistId}"
                    )
                    val formattedTitle = PlaylistFormatter.getDisplayTitle(tempStub, application) { en, jp -> jp?.takeIf { it.isNotBlank() } ?: en }
                    val formattedDescription = PlaylistFormatter.getDisplayDescription(tempStub, application) { en, jp -> jp?.takeIf { it.isNotBlank() } ?: en }
                    PlaylistEntity(
                        playlistId = uniqueNegativeId, serverId = starredItem.playlistId, name = formattedTitle,
                        description = formattedDescription, createdAt = now, last_modified_at = now, owner = null
                    )
                }
            }

            val starredServerIds = starred.map { it.playlistId }.toSet()
            val uniqueUserPlaylists = userPlaylists.filter { it.serverId !in starredServerIds }

            syntheticPlaylists + uniqueUserPlaylists + starredAsDisplayable
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Used for the "Select Playlist" dialog (excludes synthetic lists)
    val userPlaylists: StateFlow<List<PlaylistEntity>> = holodexRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    val showCreatePlaylistDialog: StateFlow<Boolean> = _showCreatePlaylistDialog.asStateFlow()

    private val _pendingItemForPlaylist = MutableStateFlow<PendingPlaylistItemDetails?>(null)

    private val _showSelectPlaylistDialog = MutableStateFlow(false)
    val showSelectPlaylistDialog: StateFlow<Boolean> = _showSelectPlaylistDialog.asStateFlow()

    fun openCreatePlaylistDialog() {
        _showCreatePlaylistDialog.value = true
    }

    fun closeCreatePlaylistDialog() {
        _showCreatePlaylistDialog.value = false
    }

    fun cancelAddToPlaylistFlow() {
        _showSelectPlaylistDialog.value = false
        _showCreatePlaylistDialog.value = false
        _pendingItemForPlaylist.value = null
    }

    fun prepareItemForPlaylistAddition(item: UnifiedDisplayItem) {
        _pendingItemForPlaylist.value = PendingPlaylistItemDetails(
            videoId = item.videoId,
            itemType = item.itemTypeForPlaylist,
            titleForDisplay = item.title,
            artistForDisplay = item.artistText,
            artworkForDisplay = item.artworkUrls.firstOrNull(),
            songStartSeconds = item.songStartSec,
            songEndSeconds = item.songEndSec,
            isExternal = item.isExternal
        )
        _showSelectPlaylistDialog.value = true
        Timber.d("$TAG: Preparing item for playlist addition: ${_pendingItemForPlaylist.value}")
    }

    fun addItemToExistingPlaylist(playlist: PlaylistEntity) {
        val pendingItem = _pendingItemForPlaylist.value ?: return cancelAddToPlaylistFlow()

        if (playlist.playlistId <= 0 && playlist.serverId == null) {
            Toast.makeText(application, "Cannot add items to this type of playlist.", Toast.LENGTH_SHORT).show()
            return cancelAddToPlaylistFlow()
        }

        viewModelScope.launch {
            try {
                if (!pendingItem.isExternal) {
                    val updatedPlaylist = playlist.copy(
                        syncStatus = SyncStatus.DIRTY,
                        last_modified_at = Instant.now().toString()
                    )
                    holodexRepository.playlistDao.updatePlaylist(updatedPlaylist)
                }

                val lastOrder = holodexRepository.getLastItemOrderInPlaylist(playlist.playlistId)
                val newOrder = (lastOrder ?: -1) + 1

                val playlistItemEntity = PlaylistItemEntity(
                    playlistOwnerId = playlist.playlistId,
                    // FIX: Construct IDs manually or move helper to PlaylistItemEntity.
                    // For now, simple manual construction is safest:
                    itemIdInPlaylist = if (pendingItem.itemType == LikedItemType.SONG_SEGMENT && pendingItem.songStartSeconds != null) {
                        "${pendingItem.videoId}_${pendingItem.songStartSeconds}"
                    } else {
                        pendingItem.videoId
                    },
                    videoIdForItem = pendingItem.videoId,
                    itemTypeInPlaylist = pendingItem.itemType,
                    songStartSecondsPlaylist = pendingItem.songStartSeconds,
                    songEndSecondsPlaylist = pendingItem.songEndSeconds,
                    songNamePlaylist = if (pendingItem.itemType == LikedItemType.SONG_SEGMENT) pendingItem.titleForDisplay else null,
                    songArtistTextPlaylist = pendingItem.artistForDisplay,
                    songArtworkUrlPlaylist = pendingItem.artworkForDisplay,
                    itemOrder = newOrder,
                    isLocalOnly = pendingItem.isExternal,
                    syncStatus = SyncStatus.DIRTY
                )

                holodexRepository.addPlaylistItem(playlistItemEntity)
                Toast.makeText(application, "'${pendingItem.titleForDisplay}' added to ${playlist.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Failed to add item to playlist ${playlist.name}")
                Toast.makeText(application, "Failed to add to playlist: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                cancelAddToPlaylistFlow()
            }
        }
    }

    fun handleCreateNewPlaylistFromSelectionDialog() {
        _showSelectPlaylistDialog.value = false
        _showCreatePlaylistDialog.value = true
    }

    fun confirmCreatePlaylist(name: String, description: String?) {
        val playlistName = name.trim()
        if (playlistName.isBlank()) {
            Toast.makeText(application, "Playlist name cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentPendingItem = _pendingItemForPlaylist.value
        viewModelScope.launch {
            try {
                val newPlaylistId = holodexRepository.createNewPlaylist(playlistName, description?.trim())
                Toast.makeText(application, "Playlist '$playlistName' created", Toast.LENGTH_SHORT).show()
                _showCreatePlaylistDialog.value = false

                if (currentPendingItem != null) {
                    addItemToExistingPlaylist(
                        PlaylistEntity(
                            playlistId = newPlaylistId, name = playlistName, description = description?.trim(),
                            createdAt = Instant.now().toString(), last_modified_at = Instant.now().toString(),
                            serverId = null, owner = null
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to create playlist '$playlistName'")
                Toast.makeText(application, "Failed to create playlist: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun prepareItemForPlaylistAdditionFromPlaybackItem(item: PlaybackItem) {
        _pendingItemForPlaylist.value = PendingPlaylistItemDetails(
            videoId = item.videoId,
            itemType = if (item.songId != null) LikedItemType.SONG_SEGMENT else LikedItemType.VIDEO,
            titleForDisplay = item.title,
            artistForDisplay = item.artistText,
            artworkForDisplay = item.artworkUri,
            songStartSeconds = item.clipStartSec?.toInt(),
            songEndSeconds = item.clipEndSec?.toInt(),
            isExternal = item.isExternal
        )
        _showSelectPlaylistDialog.value = true
    }
    fun deletePlaylist(playlist: PlaylistEntity) {
        val idToDelete = playlist.playlistId
        // This logic handles starred playlists (negative ID, has serverId) and user playlists (positive ID)
        if (idToDelete == 0L || (idToDelete < 0 && playlist.serverId == null)) {
            Toast.makeText(application, "Cannot delete synthetic playlists.", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            try {
                holodexRepository.deletePlaylist(idToDelete)
                Toast.makeText(application, "Playlist deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete playlist ID: $idToDelete")
                Toast.makeText(application, "Failed to delete playlist: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}