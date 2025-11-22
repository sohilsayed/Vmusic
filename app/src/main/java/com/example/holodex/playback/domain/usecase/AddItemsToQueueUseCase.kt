package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.repository.PlaybackRepository


class AddItemsToQueueUseCase(private val playbackRepository: PlaybackRepository) {
    // Appends to the end if index is null
    suspend operator fun invoke(items: List<PlaybackItem>, index: Int? = null) {
        playbackRepository.addItemsToQueue(items, index)
    }
}