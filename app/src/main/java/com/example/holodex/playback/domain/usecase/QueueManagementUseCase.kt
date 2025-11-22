package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.repository.PlaybackRepository
import javax.inject.Inject

class QueueManagementUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    suspend fun add(item: PlaybackItem) = repository.addItemToQueue(item, null)
    suspend fun addAll(items: List<PlaybackItem>) = repository.addItemsToQueue(items, null)
    suspend fun remove(index: Int) = repository.removeItemFromQueue(index)
    suspend fun move(from: Int, to: Int) = repository.reorderQueueItem(from, to)
    suspend fun clear() = repository.clearQueue()
}