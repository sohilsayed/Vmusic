package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.DomainPlaybackState
import com.example.holodex.playback.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow

class ObservePlaybackStateUseCase(
    private val playbackRepository: PlaybackRepository
) {
    operator fun invoke(): Flow<DomainPlaybackState> = playbackRepository.observePlaybackState()
}