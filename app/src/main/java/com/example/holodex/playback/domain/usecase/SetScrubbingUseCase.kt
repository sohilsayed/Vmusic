package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.repository.PlaybackRepository

class SetScrubbingUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke(isScrubbing: Boolean) {
        playbackRepository.setScrubbing(isScrubbing)
    }
}