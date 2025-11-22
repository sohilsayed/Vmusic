// File: java/com/example/holodex/viewmodel/PlaybackViewModel.kt
package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.playback.domain.model.DomainPlaybackProgress
import com.example.holodex.playback.domain.model.DomainPlaybackState
import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.repository.PlaybackRepository
import com.example.holodex.playback.domain.usecase.AddItemToQueueUseCase
import com.example.holodex.playback.domain.usecase.AddItemsToQueueUseCase
import com.example.holodex.playback.domain.usecase.ClearQueueUseCase
import com.example.holodex.playback.domain.usecase.GetPlayerSessionIdUseCase
import com.example.holodex.playback.domain.usecase.ObserveCurrentPlayingItemUseCase
import com.example.holodex.playback.domain.usecase.ObservePlaybackProgressUseCase
import com.example.holodex.playback.domain.usecase.ObservePlaybackQueueUseCase
import com.example.holodex.playback.domain.usecase.ObservePlaybackStateUseCase
import com.example.holodex.playback.domain.usecase.PausePlaybackUseCase
import com.example.holodex.playback.domain.usecase.PlayItemsUseCase
import com.example.holodex.playback.domain.usecase.RemoveItemFromQueueUseCase
import com.example.holodex.playback.domain.usecase.ReorderQueueItemUseCase
import com.example.holodex.playback.domain.usecase.ResumePlaybackUseCase
import com.example.holodex.playback.domain.usecase.SeekPlaybackUseCase
import com.example.holodex.playback.domain.usecase.SetRepeatModeUseCase
import com.example.holodex.playback.domain.usecase.SetScrubbingUseCase
import com.example.holodex.playback.domain.usecase.SetShuffleModeUseCase
import com.example.holodex.playback.domain.usecase.SkipToNextItemUseCase
import com.example.holodex.playback.domain.usecase.SkipToPreviousItemUseCase
import com.example.holodex.playback.domain.usecase.SkipToQueueItemUseCase
import com.example.holodex.viewmodel.autoplay.ContinuationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class PlaybackUiState(
    val currentItem: PlaybackItem? = null,
    val isPlaying: Boolean = false,
    val progress: DomainPlaybackProgress = DomainPlaybackProgress.NONE,
    val queue: List<PlaybackItem> = emptyList(),
    val currentIndexInQueue: Int = -1,
    val repeatMode: DomainRepeatMode = DomainRepeatMode.NONE,
    val shuffleMode: DomainShuffleMode = DomainShuffleMode.OFF,
    val isLoading: Boolean = false
)

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val playItemsUseCase: PlayItemsUseCase,
    private val pausePlaybackUseCase: PausePlaybackUseCase,
    private val resumePlaybackUseCase: ResumePlaybackUseCase,
    private val seekPlaybackUseCase: SeekPlaybackUseCase,
    private val skipToNextItemUseCase: SkipToNextItemUseCase,
    private val skipToPreviousItemUseCase: SkipToPreviousItemUseCase,
    private val setRepeatModeUseCase: SetRepeatModeUseCase,
    private val setShuffleModeUseCase: SetShuffleModeUseCase,
    observeCurrentPlayingItemUseCase: ObserveCurrentPlayingItemUseCase,
    observePlaybackStateUseCase: ObservePlaybackStateUseCase,
    continuationManager: ContinuationManager,
    observePlaybackProgressUseCase: ObservePlaybackProgressUseCase,
    observePlaybackQueueUseCase: ObservePlaybackQueueUseCase,
    private val setScrubbingUseCase: SetScrubbingUseCase,
    private val addItemToQueueUseCase: AddItemToQueueUseCase,
    private val addItemsToQueueUseCase: AddItemsToQueueUseCase,
    private val removeItemFromQueueUseCase: RemoveItemFromQueueUseCase,
    private val reorderQueueItemUseCase: ReorderQueueItemUseCase,
    private val clearQueueUseCase: ClearQueueUseCase,
    private val skipToQueueItemUseCase: SkipToQueueItemUseCase,
    private val getPlayerSessionIdUseCase: GetPlayerSessionIdUseCase,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {
    companion object {
        private const val TAG = "PlaybackViewModel"
    }
    private val _isVmPreparingPlayback = MutableStateFlow(false)

    val isRadioModeActive: StateFlow<Boolean> = continuationManager.isRadioModeActive
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val uiState: StateFlow<PlaybackUiState> = combine(
        observeCurrentPlayingItemUseCase(),
        observePlaybackStateUseCase(),
        observePlaybackProgressUseCase(),
        observePlaybackQueueUseCase(),
        _isVmPreparingPlayback.asStateFlow()
    ) { currentItem, domainPlayerState, progress, queueData, vmIsPreparing ->
        val isActuallyPlaying = domainPlayerState == DomainPlaybackState.PLAYING
        val isBufferingFromPlayer = domainPlayerState == DomainPlaybackState.BUFFERING
        PlaybackUiState(
            currentItem,
            isActuallyPlaying,
            progress,
            queueData.items,
            queueData.currentIndex,
            queueData.repeatMode,
            queueData.shuffleMode,
            isBufferingFromPlayer || vmIsPreparing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = PlaybackUiState(isLoading = true)
    )

    init {
        Timber.d("$TAG initialized.")
        viewModelScope.launch {
            uiState.first()
            _isVmPreparingPlayback.value = false
        }
    }

    fun playItems(items: List<PlaybackItem>, startIndex: Int = 0, startPositionMs: Long = 0L, shouldShuffle: Boolean? = null) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            _isVmPreparingPlayback.value = true
            try {
                val effectiveShuffle = shouldShuffle ?: (uiState.value.shuffleMode == DomainShuffleMode.ON)
                playItemsUseCase(items, startIndex, startPositionMs, effectiveShuffle)
            } finally {
                _isVmPreparingPlayback.value = false
            }
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            if (uiState.value.isPlaying) {
                pausePlaybackUseCase()
            } else if (uiState.value.currentItem == null && uiState.value.queue.isNotEmpty()) {
                val startIndex = uiState.value.currentIndexInQueue.coerceAtLeast(0)
                val startPosMs = if (uiState.value.currentIndexInQueue == startIndex) uiState.value.progress.positionSec * 1000L else 0L
                playItems(uiState.value.queue, startIndex, startPosMs)
            } else {
                resumePlaybackUseCase()
            }
        }
    }

    fun seekTo(positionSec: Long) = viewModelScope.launch { seekPlaybackUseCase(positionSec) }
    fun skipToNext() = viewModelScope.launch { skipToNextItemUseCase() }
    fun skipToPrevious() = viewModelScope.launch { skipToPreviousItemUseCase() }
    fun setScrubbing(isScrubbing: Boolean) = viewModelScope.launch { setScrubbingUseCase(isScrubbing) }

    fun toggleRepeatMode() = viewModelScope.launch {
        val nextMode = when (uiState.value.repeatMode) {
            DomainRepeatMode.NONE -> DomainRepeatMode.ALL
            DomainRepeatMode.ALL -> DomainRepeatMode.ONE
            DomainRepeatMode.ONE -> DomainRepeatMode.NONE
        }
        setRepeatModeUseCase(nextMode)
    }

    fun toggleShuffleMode() = viewModelScope.launch {
        val nextMode = if (uiState.value.shuffleMode == DomainShuffleMode.ON) DomainShuffleMode.OFF else DomainShuffleMode.ON
        setShuffleModeUseCase(nextMode)
    }

    fun playQueueItemAtIndex(index: Int) {
        viewModelScope.launch {
            if (index in uiState.value.queue.indices) {
                skipToQueueItemUseCase(index)
            }
        }
    }

    fun addItemToQueue(item: PlaybackItem, index: Int? = null) = viewModelScope.launch { addItemToQueueUseCase(item, index) }
    fun addItemsToQueue(items: List<PlaybackItem>, index: Int? = null) = viewModelScope.launch { addItemsToQueueUseCase(items, index) }
    fun removeItemFromQueue(index: Int) = viewModelScope.launch { removeItemFromQueueUseCase(index) }
    fun reorderQueueItem(fromIndex: Int, toIndex: Int) = viewModelScope.launch { reorderQueueItemUseCase(fromIndex, toIndex) }
    fun clearCurrentQueue() = viewModelScope.launch { clearQueueUseCase() }
    fun getAudioSessionId(): Int? = getPlayerSessionIdUseCase()
}