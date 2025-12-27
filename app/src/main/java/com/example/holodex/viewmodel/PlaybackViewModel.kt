package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.holodex.playback.domain.model.DomainPlaybackProgress
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.player.PlaybackController
import com.example.holodex.viewmodel.autoplay.ContinuationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PlaybackUiState(
    val currentItem: PlaybackItem? = null,
    val isPlaying: Boolean = false,
    val progress: DomainPlaybackProgress = DomainPlaybackProgress.NONE,
    val queue: List<PlaybackItem> = emptyList(),
    val currentIndexInQueue: Int = -1,
    val repeatMode: com.example.holodex.playback.domain.model.DomainRepeatMode = com.example.holodex.playback.domain.model.DomainRepeatMode.NONE,
    val shuffleMode: com.example.holodex.playback.domain.model.DomainShuffleMode = com.example.holodex.playback.domain.model.DomainShuffleMode.OFF,
    val isLoading: Boolean = false
)

@UnstableApi
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val controller: PlaybackController,
    continuationManager: ContinuationManager
) : ViewModel() {

    val isRadioModeActive = continuationManager.isRadioModeActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val uiState: StateFlow<PlaybackUiState> = controller.state.map { s ->
        PlaybackUiState(
            currentItem = s.activeQueue.getOrNull(s.currentIndex),
            isPlaying = s.isPlaying,
            progress = DomainPlaybackProgress(s.progressMs / 1000, s.durationMs / 1000, 0),
            queue = s.activeQueue,
            currentIndexInQueue = s.currentIndex,
            repeatMode = s.repeatMode,
            shuffleMode = s.shuffleMode,
            isLoading = s.isLoading
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PlaybackUiState()
    )

    // --- COMMANDS ---

    fun playItems(items: List<PlaybackItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        controller.loadAndPlay(items, startIndex, startPositionMs)
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun seekTo(positionSec: Long) = controller.seekTo(positionSec * 1000L)
    fun skipToNext() = controller.skipToNext()
    fun skipToPrevious() = controller.skipToPrevious()

    // FIX: Add Toggle Repeat Logic
    fun toggleRepeatMode() {
        // Simple toggle: OFF -> ONE -> ALL -> OFF
        val current = controller.exoPlayer.repeatMode
        val next = when (current) {
            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ONE
            androidx.media3.common.Player.REPEAT_MODE_ONE -> androidx.media3.common.Player.REPEAT_MODE_ALL
            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
        }
        controller.exoPlayer.repeatMode = next
    }

    fun toggleShuffleMode() = controller.toggleShuffle()

    fun playQueueItemAtIndex(index: Int) = controller.exoPlayer.seekToDefaultPosition(index)

    // FIX: Call Controller methods to ensure persistence/state sync
    fun removeItemFromQueue(index: Int) = controller.removeItem(index)
    fun reorderQueueItem(from: Int, to: Int) = controller.moveItem(from, to)
    fun clearCurrentQueue() = controller.clearQueue()

    // Stub for old scrubbing logic
    fun setScrubbing(isScrubbing: Boolean) {}

    fun getAudioSessionId(): Int? = controller.exoPlayer.audioSessionId
}