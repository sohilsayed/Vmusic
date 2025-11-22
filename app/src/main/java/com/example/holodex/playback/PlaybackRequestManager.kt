// File: java/com/example/holodex/playback/PlaybackRequestManager.kt
package com.example.holodex.playback

import com.example.holodex.playback.domain.model.PlaybackItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PlaybackRequestData(
    val items: List<PlaybackItem>,
    val startIndex: Int = 0,
    val startPositionSec: Long = 0L,
    val shouldShuffle: Boolean = false
)

/**
 * A singleton class to centralize playback requests from any part of the app.
 */
class PlaybackRequestManager {
    private val _playbackRequest = MutableSharedFlow<PlaybackRequestData>()
    val playbackRequest: SharedFlow<PlaybackRequestData> = _playbackRequest.asSharedFlow()

    suspend fun submitPlaybackRequest(
        items: List<PlaybackItem>,
        startIndex: Int = 0,
        startPositionSec: Long = 0L,
        shouldShuffle: Boolean = false
    ) {
        if (items.isEmpty()) return
        _playbackRequest.emit(
            PlaybackRequestData(items, startIndex, startPositionSec, shouldShuffle)
        )
    }
}