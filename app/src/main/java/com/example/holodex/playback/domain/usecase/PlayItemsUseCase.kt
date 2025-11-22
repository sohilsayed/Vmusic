// File: java/com/example/holodex/playback/domain/usecase/PlayItemsUseCase.kt
package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.repository.PlaybackRepository

class PlayItemsUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke(
        items: List<PlaybackItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        shouldShuffle: Boolean = false
    ) {
        if (items.isEmpty()) return
        playbackRepository.prepareAndPlay(items, startIndex, startPositionMs, shouldShuffle)
    }
}