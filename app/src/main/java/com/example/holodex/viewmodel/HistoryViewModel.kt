// File: java/com/example/holodex/viewmodel/HistoryViewModel.kt

package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.playback.PlaybackRequestManager
import com.example.holodex.playback.domain.usecase.AddItemsToQueueUseCase
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val holodexRepository: HolodexRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackRequestManager: PlaybackRequestManager,
    private val addItemsToQueueUseCase: AddItemsToQueueUseCase
) : ViewModel() {

    private val _transientMessage = MutableSharedFlow<String>()
    val transientMessage: SharedFlow<String> = _transientMessage.asSharedFlow()

    // --- REVERT TO THE CORRECT THREE-WAY COMBINE PATTERN ---
    val unifiedHistoryItems: StateFlow<List<UnifiedDisplayItem>> =
        combine(
            holodexRepository.getHistory(),
            holodexRepository.likedItemIds, // This is now a reliable StateFlow
            downloadRepository.getAllDownloads().map { list -> list.map { it.videoId }.toSet() }
        ) { historyEntities, likedIds, downloadedIds ->
            // This transformation will now run automatically whenever history, likes, or downloads change.
            historyEntities.map { entity ->
                entity.toUnifiedDisplayItem(
                    isDownloaded = downloadedIds.contains(entity.itemId),
                    isLiked = likedIds.contains(entity.itemId)
                )
            }
        }
            .catch { e ->
                Timber.e(e, "Error combining history items.")
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )
    // --- END OF CORRECTION ---


    fun playFromHistoryItem(tappedItem: UnifiedDisplayItem) {
        viewModelScope.launch {
            val currentHistory = unifiedHistoryItems.value
            val tappedIndex = currentHistory.indexOf(tappedItem)

            if (tappedIndex == -1) {
                _transientMessage.emit("Error: Could not find item to play.")
                return@launch
            }

            val playbackItems = currentHistory.map { it.toPlaybackItem() }
            playbackRequestManager.submitPlaybackRequest(
                items = playbackItems,
                startIndex = tappedIndex
            )
        }
    }

    fun playAllHistory() {
        viewModelScope.launch {
            val playbackItems = unifiedHistoryItems.value.map { it.toPlaybackItem() }
            if (playbackItems.isNotEmpty()) {
                playbackRequestManager.submitPlaybackRequest(items = playbackItems)
            } else {
                _transientMessage.emit("History is empty.")
            }
        }
    }

    fun addAllHistoryToQueue() {
        viewModelScope.launch {
            val playbackItems = unifiedHistoryItems.value.map { it.toPlaybackItem() }
            if (playbackItems.isNotEmpty()) {
                addItemsToQueueUseCase(playbackItems)
                _transientMessage.emit("Added ${playbackItems.size} songs to queue.")
            } else {
                _transientMessage.emit("History is empty.")
            }
        }
    }
}