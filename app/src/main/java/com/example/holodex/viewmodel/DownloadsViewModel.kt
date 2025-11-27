package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.playback.PlaybackRequestManager
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import javax.inject.Inject

data class DownloadsState(
    val items: ImmutableList<UnifiedDisplayItem> = persistentListOf(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

sealed class DownloadsSideEffect {
    data class ShowToast(val message: String) : DownloadsSideEffect()
}

@UnstableApi
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val unifiedRepository: UnifiedVideoRepository,
    private val downloadRepository: DownloadRepository,
    private val holodexRepository: HolodexRepository,
    private val playbackRequestManager: PlaybackRequestManager
) : ContainerHost<DownloadsState, DownloadsSideEffect>, ViewModel() {

    companion object {
        private const val TAG = "DownloadsViewModel"
    }

    private val queryFlow = MutableStateFlow("")

    override val container = container<DownloadsState, DownloadsSideEffect>(DownloadsState()) {
        observeDownloads()
    }

    private fun observeDownloads() = intent {
        combine(
            unifiedRepository.getDownloads(),
            queryFlow
        ) { downloads: List<UnifiedDisplayItem>, query: String ->
            if (query.isBlank()) {
                downloads
            } else {
                downloads.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.artistText.contains(query, ignoreCase = true)
                }
            }
        }.collect { filteredList ->
            reduce {
                state.copy(
                    items = filteredList.toImmutableList(),
                    isLoading = false,
                    searchQuery = queryFlow.value
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        queryFlow.value = query
    }

    fun playDownloads(tappedItem: UnifiedDisplayItem) = intent {
        if (!tappedItem.isDownloaded) {
            postSideEffect(DownloadsSideEffect.ShowToast("Item is not fully downloaded."))
            return@intent
        }

        // Filter current list for only downloaded items to play in queue
        val playableItems = state.items.filter { it.isDownloaded }

        if (playableItems.isEmpty()) return@intent

        val playbackItems = playableItems.map { it.toPlaybackItem() }
        val startIndex = playbackItems.indexOfFirst { it.id == tappedItem.playbackItemId }.coerceAtLeast(0)

        viewModelScope.launch {
            playbackRequestManager.submitPlaybackRequest(playbackItems, startIndex)
        }
    }

    fun playAllDownloadsShuffled() = intent {
        val playableItems = state.items.filter { it.isDownloaded }
        if (playableItems.isNotEmpty()) {
            val playbackItems = playableItems.map { it.toPlaybackItem() }
            viewModelScope.launch {
                playbackRequestManager.submitPlaybackRequest(playbackItems, 0, shouldShuffle = true)
            }
        } else {
            postSideEffect(DownloadsSideEffect.ShowToast("No playable downloads found."))
        }
    }

    fun deleteDownload(itemId: String) = intent {
        viewModelScope.launch {
            try {
                downloadRepository.deleteDownloadById(itemId)
                postSideEffect(DownloadsSideEffect.ShowToast("Download deleted"))
            } catch (e: Exception) {
                postSideEffect(DownloadsSideEffect.ShowToast("Failed to delete download"))
            }
        }
    }

    fun cancelDownload(itemId: String) = intent {
        viewModelScope.launch {
            try {
                downloadRepository.cancelDownload(itemId)
                postSideEffect(DownloadsSideEffect.ShowToast("Download cancelled"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to cancel $itemId")
            }
        }
    }

    fun resumeDownload(itemId: String) = intent {
        viewModelScope.launch {
            try {
                downloadRepository.resumeDownload(itemId)
                postSideEffect(DownloadsSideEffect.ShowToast("Resuming download..."))
            } catch (e: Exception) {
                Timber.e(e, "Failed to resume $itemId")
            }
        }
    }

    fun retryExport(item: UnifiedDisplayItem) = intent {
        // Retry logic using UnifiedDisplayItem ID
        viewModelScope.launch {
            try {
                downloadRepository.retryExport(item.playbackItemId)
                postSideEffect(DownloadsSideEffect.ShowToast("Retrying export..."))
            } catch (e: Exception) {
                Timber.e(e, "Failed to retry export")
            }
        }
    }

    fun retryDownload(item: UnifiedDisplayItem) = intent {
        viewModelScope.launch {
            val result = holodexRepository.getVideoWithSongs(item.videoId, forceRefresh = true)
            result.onSuccess { videoWithSongs ->
                val songToRetry = videoWithSongs.songs?.find { it.start == (item.songStartSec ?: 0) }
                if (songToRetry != null) {
                    downloadRepository.startDownload(videoWithSongs, songToRetry)
                    postSideEffect(DownloadsSideEffect.ShowToast("Retrying download..."))
                }
            }
        }
    }

    // *** Helper function required by DownloadsScreen.kt ***
    fun mapDownloadToPlaybackItem(item: com.example.holodex.data.db.DownloadedItemEntity): PlaybackItem {
        return item.toPlaybackItem()
    }
}