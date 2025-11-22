// File: java/com/example/holodex/playback/domain/model/PersistedPlaybackData.kt
package com.example.holodex.playback.domain.model

data class PersistedPlaybackData(
    val queueId: String,
    val queueItems: List<PersistedPlaybackItem>,
    val currentIndex: Int,
    val currentPositionSec: Long,
    val currentItemId: String?,
    val repeatMode: DomainRepeatMode,
    val shuffleMode: DomainShuffleMode,
    val shuffledQueueItemIds: List<String>? = null
)

data class PersistedPlaybackItem(
    val id: String,
    val videoId: String,
    val songId: String?,
    val title: String,
    val artistText: String,
    val albumText: String?,
    val artworkUri: String?,
    val durationSec: Long,
    val description: String? = null,
    val channelId: String,
    val clipStartSec: Long? = null,
    val clipEndSec: Long? = null
)