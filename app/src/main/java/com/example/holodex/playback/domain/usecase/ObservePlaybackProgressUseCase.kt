package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.DomainPlaybackProgress
import com.example.holodex.playback.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow


class ObservePlaybackProgressUseCase(
    private val playbackRepository: PlaybackRepository
) {
    operator fun invoke(): Flow<DomainPlaybackProgress> = playbackRepository.observePlaybackProgress()
}