package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.domain.repository.PlaybackRepository


class SetShuffleModeUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke(mode: DomainShuffleMode) {
        playbackRepository.setShuffleMode(mode)
    }
}