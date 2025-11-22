// File: java/com/example/holodex/data/db/LocalEntities.kt

package com.example.holodex.data.db

import androidx.room.ColumnInfo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_favorites")
data class LocalFavoriteEntity(
    @PrimaryKey val itemId: String,
    val videoId: String,
    val channelId: String,
    val title: String,
    val artistText: String,
    val artworkUrl: String?,
    val durationSec: Long,
    val isSegment: Boolean,
    val songStartSec: Int?,
    val songEndSec: Int?
)

@Entity(tableName = "external_channels")
data class ExternalChannelEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val photoUrl: String?,
    val lastCheckedTimestamp: Long = 0,
    val status: String = "OK",
    val errorCount: Int = 0
)

@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val localPlaylistId: Long = 0,
    val name: String,
    val description: String?,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "local_playlist_items",
    primaryKeys = ["playlistOwnerId", "itemId"]
)
data class LocalPlaylistItemEntity(
    val playlistOwnerId: Long,
    val itemId: String, // The composite ID: "videoId_startTime"
    val videoId: String,
    val itemOrder: Int,

    // Snapshot data for quick display
    val title: String,
    val artistText: String,
    val artworkUrl: String?,
    val durationSec: Long,
    val channelId: String,
    val isSegment: Boolean,
    val songStartSec: Int?,
    val songEndSec: Int?
)
