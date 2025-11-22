package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.repository.PlaybackRepository

class RemoveItemFromQueueUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke(index: Int) {
        playbackRepository.removeItemFromQueue(index)
    }
}