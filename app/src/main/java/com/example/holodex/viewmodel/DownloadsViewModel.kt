// File: java/com/example/holodex/viewmodel/DownloadsViewModel.kt

package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.DownloadedItemEntity
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.playback.PlaybackRequestManager
import com.example.holodex.playback.domain.model.PlaybackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class DownloadsViewModel @UnstableApi
@Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val holodexRepository: HolodexRepository,
    private val playbackRequestManager: PlaybackRequestManager
) : ViewModel() {

    companion object {
        private const val TAG = "DownloadsViewModel"
    }

    private val allDownloads: StateFlow<List<DownloadedItemEntity>> =
        downloadRepository.getAllDownloads()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredDownloads: StateFlow<ImmutableList<DownloadedItemEntity>> =
        combine(allDownloads, _searchQuery) { downloads, query ->
            val list = if (query.isBlank()) {
                downloads
            } else {
                downloads.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.artistText.contains(query, ignoreCase = true)
                }
            }
            list.toImmutableList() // Convert here
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun retryDownload(item: DownloadedItemEntity) {
        viewModelScope.launch {
            try {
                Timber.d("$TAG: Retrying download for item: ${item.videoId}")
                val videoId = item.videoId.substringBeforeLast('_')
                val songStart = item.videoId.substringAfterLast('_').toIntOrNull()
                Timber.d("$TAG: Retrying download for item: ${item.videoId} (Parent: $videoId, Start: $songStart)")

                if (songStart == null) {
                    Timber.e("$TAG: Cannot retry, invalid item ID format: ${item.videoId}")
                    return@launch
                }

                val result = holodexRepository.getVideoWithSongs(videoId, forceRefresh = true)
                result.onSuccess { videoWithSongs ->
                    val songToRetry = videoWithSongs.songs?.find { it.start == songStart }
                    if (songToRetry != null) {
                        Timber.i("$TAG: Found matching song to retry: '${songToRetry.name}'. Starting download.")
                        downloadRepository.startDownload(videoWithSongs, songToRetry)
                    } else {
                        Timber.e("$TAG: Could not find matching song with start time $songStart in video $videoId to retry.")
                    }
                }.onFailure { exception ->
                    Timber.e(exception, "$TAG: Failed to fetch video details for retry.")
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error during retry download for ${item.videoId}")
            }
        }
    }


    fun playDownloads(tappedItem: DownloadedItemEntity) {
        if (tappedItem.downloadStatus != DownloadStatus.COMPLETED) {
            Timber.w("$TAG: Attempted to play a non-completed download: ${tappedItem.videoId}. Status: ${tappedItem.downloadStatus}")
            return
        }
        if (tappedItem.localFileUri.isNullOrBlank()) {
            Timber.e("$TAG: Tapped item ${tappedItem.videoId} is completed but has no local file URI.")
            return
        }

        viewModelScope.launch {
            val currentVisibleDownloads = filteredDownloads.value
                .filter { it.downloadStatus == DownloadStatus.COMPLETED }

            val playableDownloads = currentVisibleDownloads.filter { !it.localFileUri.isNullOrBlank() }

            if (playableDownloads.isEmpty()) {
                Timber.e("$TAG: Play request initiated, but the visible download list is empty or contains no playable items.")
                return@launch
            }

            val playbackItems = playableDownloads.map { mapDownloadToPlaybackItem(it) }
            val startIndex = playbackItems.indexOfFirst { it.id == tappedItem.videoId }.coerceAtLeast(0)
            Timber.i("$TAG: Playing downloads queue. Tapped index: $startIndex, Total items in queue: ${playbackItems.size}")
            playbackRequestManager.submitPlaybackRequest(playbackItems, startIndex)
        }
    }

    fun playAllDownloadsShuffled() {
        viewModelScope.launch {
            val completedDownloads = filteredDownloads.value
                .filter { it.downloadStatus == DownloadStatus.COMPLETED }

            val playableDownloads = completedDownloads.filter { !it.localFileUri.isNullOrBlank() }

            if (playableDownloads.isNotEmpty()) {
                val playbackItems = playableDownloads.map { mapDownloadToPlaybackItem(it) }
                playbackRequestManager.submitPlaybackRequest(playbackItems, 0, shouldShuffle = true)
            } else {
                Timber.w("$TAG: No playable downloads found for shuffle playback.")
            }
        }
    }

    fun deleteDownload(itemId: String) {
        viewModelScope.launch {
            Timber.d("$TAG: Deleting download with ID: $itemId")
            try {
                downloadRepository.deleteDownloadById(itemId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete download: $itemId")
            }
        }
    }

    @UnstableApi
    fun cancelDownload(videoId: String) {
        viewModelScope.launch {
            try {
                Timber.d("$TAG: Cancelling download for videoId: $videoId")
                downloadRepository.cancelDownload(videoId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to cancel download for $videoId")
            }
        }
    }

    fun resumeDownload(videoId: String) {
        viewModelScope.launch {
            try {
                Timber.d("$TAG: Resuming download for videoId: $videoId")
                downloadRepository.resumeDownload(videoId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to resume download for $videoId")
            }
        }
    }

    fun purgeStaleDownloads() {
        viewModelScope.launch {
            try {
                Timber.d("$TAG: Purging stale downloads (now running full reconciliation)")
                downloadRepository.reconcileAllDownloads()
            } catch (e: Exception) {
                Timber.e(e, "Failed to reconcile downloads")
            }
        }
    }

    /**
     * Relays the request to re-trigger the AudioProcessingWorker to the repository.
     * This is for items that have been successfully downloaded but failed during post-processing.
     */
    fun retryExport(item: DownloadedItemEntity) {
        viewModelScope.launch {
            Timber.d("$TAG: Relaying retry export request for ${item.videoId} to repository.")
            try {
                downloadRepository.retryExportForItem(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to relay retry export for ${item.videoId}")
            }
        }
    }
    fun mapDownloadToPlaybackItem(item: DownloadedItemEntity): PlaybackItem {
        val parentVideoId = item.videoId.substringBeforeLast('_')
        val songStartSec = item.videoId.substringAfterLast('_').toLongOrNull() ?: 0
        return PlaybackItem(
            id = item.videoId,
            videoId = parentVideoId,
            songId = item.videoId,
            serverUuid = item.videoId, // The ID for a download IS the server's unique ID for the segment
            title = item.title,
            artistText = item.artistText,
            albumText = item.title,
            artworkUri = item.artworkUrl,
            durationSec = item.durationSec,
            streamUri = item.localFileUri,
            clipStartSec = songStartSec,
            clipEndSec = songStartSec + item.durationSec,
            description = null,
            channelId = item.channelId,
            originalArtist = item.artistText
        )
    }
}
fun PlaybackItem.toUnifiedDisplayItem(): UnifiedDisplayItem {
    return UnifiedDisplayItem(
        stableId = "download_${this.id}",
        playbackItemId = this.id,
        videoId = this.videoId,
        channelId = this.channelId,
        title = this.title,
        artistText = this.artistText,
        artworkUrls = listOfNotNull(this.artworkUri),
        durationText = com.example.holodex.playback.util.formatDurationSeconds(this.durationSec),
        isSegment = true,
        songCount = null,
        isDownloaded = true,
        isLiked = false, // We don't have this info here, FavoritesViewModel will provide it
        itemTypeForPlaylist = LikedItemType.SONG_SEGMENT,
        songStartSec = this.clipStartSec?.toInt(),
        songEndSec = this.clipEndSec?.toInt(),
        originalArtist = this.originalArtist,
        isExternal = false // Downloads are never external
    )
}