package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.PersistedPlaybackData
import com.example.holodex.playback.domain.repository.PlaybackStateRepository

class SavePlaybackStateUseCase(
    private val playbackStateRepository: PlaybackStateRepository
) {
    suspend operator fun invoke(data: PersistedPlaybackData) {
        playbackStateRepository.saveState(data)
    }
}