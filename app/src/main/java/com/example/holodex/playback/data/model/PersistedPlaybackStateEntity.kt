// File: java/com/example/holodex/playback/data/model/PersistedPlaybackStateEntity.kt
package com.example.holodex.playback.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode

@Entity(tableName = "persisted_playback_state_table")
data class PersistedPlaybackStateEntity(
    @PrimaryKey
    val queueIdentifier: String,
    val currentIndex: Int,
    val currentPositionSec: Long,
    @ColumnInfo(name = "current_item_id")
    val currentItemId: String?,
    val repeatMode: DomainRepeatMode,
    val shuffleMode: DomainShuffleMode,
    @ColumnInfo(name = "shuffled_item_ids")
    val shuffledItemIds: List<String>? = null,
    @ColumnInfo(name = "shuffle_order_version", defaultValue = "1")
    val shuffleOrderVersion: Int = 1
)