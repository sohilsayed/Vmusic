package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.repository.PlaybackRepository


class ClearQueueUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke() {
        playbackRepository.clearQueue()
    }
}