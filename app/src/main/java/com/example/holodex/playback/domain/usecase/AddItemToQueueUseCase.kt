package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.player.PlaybackController
import javax.inject.Inject

class AddItemToQueueUseCase @Inject constructor(
    private val controller: PlaybackController
) {
    operator fun invoke(item: PlaybackItem, index: Int? = null) {
        controller.addItemsToQueue(listOf(item), index)
    }
}