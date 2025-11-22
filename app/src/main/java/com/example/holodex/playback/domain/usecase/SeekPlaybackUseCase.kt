// File: holodex\playback\domain\usecase\SeekPlaybackUseCase.kt
package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.repository.PlaybackRepository

class SeekPlaybackUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke(positionSec: Long) { // Changed parameter to Sec
        playbackRepository.seekTo(positionSec)
    }
}