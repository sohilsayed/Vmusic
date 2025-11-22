// File: java/com/example/holodex/playback/data/queue/PlaybackQueueState.kt
package com.example.holodex.playback.data.queue

import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.domain.model.PlaybackItem

data class PlaybackQueueState(
    val originalList: List<PlaybackItem> = emptyList(),
    val activeList: List<PlaybackItem> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleMode: DomainShuffleMode = DomainShuffleMode.OFF,
    val repeatMode: DomainRepeatMode = DomainRepeatMode.NONE,
    val transientStartPositionMs: Long = 0L
) {
    val currentItem: PlaybackItem?
        get() = activeList.getOrNull(currentIndex)
}