package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.PersistedPlaybackData
import com.example.holodex.playback.domain.repository.PlaybackStateRepository

class LoadPlaybackStateUseCase(
    private val playbackStateRepository: PlaybackStateRepository
) {
    suspend operator fun invoke(): PersistedPlaybackData? {
        return playbackStateRepository.loadState()
    }
}