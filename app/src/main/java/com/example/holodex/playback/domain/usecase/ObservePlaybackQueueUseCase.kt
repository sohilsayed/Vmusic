package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.PlaybackQueue
import com.example.holodex.playback.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow


class ObservePlaybackQueueUseCase(
    private val playbackRepository: PlaybackRepository
) {
    operator fun invoke(): Flow<PlaybackQueue> = playbackRepository.observePlaybackQueue()
}