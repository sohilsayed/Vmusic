// File: java/com/example/holodex/playback/data/mapper/PersistedPlaybackStateMapper.kt
package com.example.holodex.playback.data.mapper

import com.example.holodex.playback.data.model.PersistedPlaybackItemEntity
import com.example.holodex.playback.data.model.PersistedPlaybackStateEntity
import com.example.holodex.playback.domain.model.PersistedPlaybackData
import com.example.holodex.playback.domain.model.PersistedPlaybackItem

fun PersistedPlaybackData.toEntities(): Pair<PersistedPlaybackStateEntity, List<PersistedPlaybackItemEntity>> {
    val stateEntity = PersistedPlaybackStateEntity(
        queueIdentifier = this.queueId,
        currentIndex = this.currentIndex,
        currentPositionSec = this.currentPositionSec * 1000L,
        currentItemId = this.currentItemId,
        repeatMode = this.repeatMode,
        shuffleMode = this.shuffleMode,
        shuffledItemIds = this.shuffledQueueItemIds,
        shuffleOrderVersion = 1
    )

    val itemEntities = this.queueItems.mapIndexed { order, domainItem ->
        PersistedPlaybackItemEntity(
            ownerQueueIdentifier = this.queueId,
            itemPlaybackId = domainItem.id,
            videoId = domainItem.videoId,
            songId = domainItem.songId,
            title = domainItem.title,
            artistText = domainItem.artistText,
            albumText = domainItem.albumText,
            artworkUri = domainItem.artworkUri,
            durationMs = domainItem.durationSec * 1000L,
            itemOrder = order,
            description = domainItem.description,
            channelId = domainItem.channelId,
            clipStartMs = domainItem.clipStartSec?.let { it * 1000L },
            clipEndMs = domainItem.clipEndSec?.let { it * 1000L }
        )
    }
    return Pair(stateEntity, itemEntities)
}

fun PersistedPlaybackStateEntity.toDomainModel(itemEntities: List<PersistedPlaybackItemEntity>): PersistedPlaybackData {
    val domainItems = itemEntities
        .sortedBy { it.itemOrder }
        .map { entity ->
            PersistedPlaybackItem(
                id = entity.itemPlaybackId,
                videoId = entity.videoId,
                songId = entity.songId,
                title = entity.title,
                artistText = entity.artistText,
                albumText = entity.albumText,
                artworkUri = entity.artworkUri,
                durationSec = entity.durationMs / 1000L,
                description = entity.description,
                channelId = entity.channelId,
                clipStartSec = entity.clipStartMs?.let { it / 1000L },
                clipEndSec = entity.clipEndMs?.let { it / 1000L }
            )
        }

    return PersistedPlaybackData(
        queueId = this.queueIdentifier,
        queueItems = domainItems,
        currentIndex = this.currentIndex,
        currentPositionSec = this.currentPositionSec / 1000L,
        currentItemId = this.currentItemId,
        repeatMode = this.repeatMode,
        shuffleMode = this.shuffleMode,
        shuffledQueueItemIds = this.shuffledItemIds
    )
}