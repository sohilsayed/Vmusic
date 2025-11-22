package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.repository.PlaybackRepository


class SetRepeatModeUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke(mode: DomainRepeatMode) {
        playbackRepository.setRepeatMode(mode)
    }
}