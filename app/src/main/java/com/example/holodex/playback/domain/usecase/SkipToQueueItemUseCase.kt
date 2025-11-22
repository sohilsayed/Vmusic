// File: java/com/example/holodex/playback/domain/usecase/SkipToQueueItemUseCase.kt

package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.repository.PlaybackRepository

class SkipToQueueItemUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke(index: Int) {
        playbackRepository.skipToQueueItem(index)
    }
}