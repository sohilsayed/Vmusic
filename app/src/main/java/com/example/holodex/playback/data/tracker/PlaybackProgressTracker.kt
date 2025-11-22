// File: java/com/example/holodex/playback/data/tracker/PlaybackProgressTracker.kt
package com.example.holodex.playback.data.tracker

import androidx.media3.common.C
import androidx.media3.common.Player
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.playback.data.mapper.MediaItemMapper
import com.example.holodex.playback.domain.model.DomainPlaybackProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class PlaybackProgressTracker(
    private val exoPlayer: Player,
    private val scope: CoroutineScope,
    private val holodexRepository: HolodexRepository,
    private val mediaItemMapper: MediaItemMapper
) {
    companion object {
        private const val TAG = "PlaybackProgressTracker"
        private const val HISTORY_SAVE_THRESHOLD_PERCENT = 50.0
    }

    private val _progressFlow = MutableStateFlow(DomainPlaybackProgress.NONE)
    val progressFlow: StateFlow<DomainPlaybackProgress> = _progressFlow.asStateFlow()

    private var progressUpdateJob: Job? = null
    private var currentMediaIdForHistory: String? = null
    private var hasBeenSavedToHistory: Boolean = false

    fun startTracking() {
        Timber.d("$TAG: Start tracking progress.")
        stopTracking()
        scope.launch {
            if (withContext(Dispatchers.Main) { exoPlayer.isPlaying }) {
                progressUpdateJob = launch {
                    while (isActive) {
                        updateProgress()
                        delay(1000L)
                    }
                }
            }
        }
    }

    fun stopTracking() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private suspend fun updateProgress() {
        if (!scope.isActive) return

        withContext(Dispatchers.Main) {
            val currentMediaItem = exoPlayer.currentMediaItem
            val currentMediaId = currentMediaItem?.mediaId
            val currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val playerDurationMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L

            val newProgress = DomainPlaybackProgress(
                positionSec = currentPositionMs / 1000L,
                durationSec = playerDurationMs / 1000L,
                bufferedPositionSec = exoPlayer.bufferedPosition.coerceAtLeast(0L) / 1000L
            )
            if (_progressFlow.value != newProgress) {
                _progressFlow.value = newProgress
            }

            if (currentMediaId == null) {
                if (currentMediaIdForHistory != null) {
                    currentMediaIdForHistory = null
                    hasBeenSavedToHistory = false
                }
                return@withContext
            }

            if (currentMediaId != currentMediaIdForHistory) {
                currentMediaIdForHistory = currentMediaId
                hasBeenSavedToHistory = false
            }

            if (hasBeenSavedToHistory) return@withContext

            val playbackPercentage = (currentPositionMs.toDouble() / playerDurationMs.toDouble()) * 100.0
            val isEligible = playbackPercentage >= HISTORY_SAVE_THRESHOLD_PERCENT

            val playbackItem = currentMediaItem.let { mediaItemMapper.toPlaybackItem(it) }
            val hasResolvedStream = !playbackItem?.streamUri.isNullOrBlank()

            if (isEligible && hasResolvedStream) {
                holodexRepository.addSongToHistory(playbackItem!!)
                hasBeenSavedToHistory = true
            }
        }
    }

    fun resetProgress() {
        _progressFlow.value = DomainPlaybackProgress.NONE
    }
}