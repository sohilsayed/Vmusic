// File: java/com/example/holodex/playback/data/model/PersistedPlaybackItemEntity.kt
package com.example.holodex.playback.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "persisted_playback_items_table",
    foreignKeys = [ForeignKey(
        entity = PersistedPlaybackStateEntity::class,
        parentColumns = ["queueIdentifier"],
        childColumns = ["ownerQueueIdentifier"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["ownerQueueIdentifier"])]
)
data class PersistedPlaybackItemEntity(
    @PrimaryKey(autoGenerate = true) val dbId: Long = 0,
    val ownerQueueIdentifier: String,
    val itemPlaybackId: String,
    val videoId: String,
    val songId: String?,
    val title: String,
    val artistText: String,
    val albumText: String?,
    val artworkUri: String?,
    val durationMs: Long,
    val itemOrder: Int,
    val description: String? = null,
    @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "clip_start_ms") val clipStartMs: Long? = null,
    @ColumnInfo(name = "clip_end_ms") val clipEndMs: Long? = null
)