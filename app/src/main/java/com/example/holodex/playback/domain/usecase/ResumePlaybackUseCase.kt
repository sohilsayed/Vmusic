package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.repository.PlaybackRepository

class ResumePlaybackUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke() {
        // 'play' can often serve as resume if the player is already prepared
        playbackRepository.play()
    }
}