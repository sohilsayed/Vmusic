// File: java/com/example/holodex/playback/data/repository/RoomPlaybackStateRepositoryImpl.kt
package com.example.holodex.playback.data.repository

import com.example.holodex.playback.data.mapper.toDomainModel
import com.example.holodex.playback.data.mapper.toEntities
import com.example.holodex.playback.data.model.PersistedPlaybackStateDao
import com.example.holodex.playback.domain.model.PersistedPlaybackData
import com.example.holodex.playback.domain.repository.PlaybackStateRepository

class RoomPlaybackStateRepositoryImpl(
    private val dao: PersistedPlaybackStateDao
) : PlaybackStateRepository {

    private val defaultQueueId = "default_queue"

    override suspend fun saveState(data: PersistedPlaybackData) {
        val (stateEntity, itemEntities) = data.toEntities()
        dao.savePlaybackStateWithItems(stateEntity, itemEntities)
    }

    override suspend fun loadState(): PersistedPlaybackData? {
        val stateWithItems = dao.getPlaybackStateWithItems(defaultQueueId)
        return stateWithItems?.let { tuple ->
            tuple.state.toDomainModel(tuple.items.sortedBy { it.itemOrder })
        }
    }

    override suspend fun clearState() {
        dao.clearPlaybackStateByQueueId(defaultQueueId)
        dao.clearPlaybackItemsByQueueId(defaultQueueId)
    }
}