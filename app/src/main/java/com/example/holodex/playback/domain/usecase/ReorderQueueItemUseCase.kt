package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.repository.PlaybackRepository


class ReorderQueueItemUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke(fromIndex: Int, toIndex: Int) {
        playbackRepository.reorderQueueItem(fromIndex, toIndex)
    }
}