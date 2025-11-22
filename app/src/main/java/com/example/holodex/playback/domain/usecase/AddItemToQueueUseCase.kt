// File: java/com/example/holodex/playback/domain/usecase/AddItemToQueueUseCase.kt
package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.repository.PlaybackRepository

class AddItemToQueueUseCase(private val playbackRepository: PlaybackRepository) {
    suspend operator fun invoke(item: PlaybackItem, index: Int? = null) {
        playbackRepository.addItemToQueue(item, index)
    }
}