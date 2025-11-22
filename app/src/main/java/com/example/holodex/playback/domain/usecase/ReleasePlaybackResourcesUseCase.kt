package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.repository.PlaybackRepository


class ReleasePlaybackResourcesUseCase(private val playbackRepository: PlaybackRepository) {
    operator fun invoke() { // Not suspend as release might be a synchronous cleanup
        playbackRepository.release()
    }
}