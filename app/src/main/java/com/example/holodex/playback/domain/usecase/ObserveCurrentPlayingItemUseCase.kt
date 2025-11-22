package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.repository.PlaybackRepository
import kotlinx.coroutines.flow.Flow


class ObserveCurrentPlayingItemUseCase(
    private val playbackRepository: PlaybackRepository
) {
    operator fun invoke(): Flow<PlaybackItem?> = playbackRepository.observeCurrentPlayingItem()
}