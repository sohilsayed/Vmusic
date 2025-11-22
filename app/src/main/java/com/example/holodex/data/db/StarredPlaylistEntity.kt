// File: java/com/example/holodex/data/db/StarredPlaylistEntity.kt (NEW FILE)
package com.example.holodex.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "starred_playlists")
data class StarredPlaylistEntity(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus
)