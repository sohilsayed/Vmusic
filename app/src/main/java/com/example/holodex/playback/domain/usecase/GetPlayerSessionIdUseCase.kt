package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.repository.PlaybackRepository


class GetPlayerSessionIdUseCase(
    private val playbackRepository: PlaybackRepository
) {
    operator fun invoke(): Int? = playbackRepository.getPlayerSessionId()
}