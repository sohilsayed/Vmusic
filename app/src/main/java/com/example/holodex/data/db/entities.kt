// File: java/com/example/holodex/data/db/entities.kt
package com.example.holodex.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.holodex.data.model.HolodexChannelMin
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import timber.log.Timber





/**
 * Represents a user-created playlist in the local Room database.
 * This class is now decoupled from the network layer and contains only the fields
 * necessary for persistence and client-side sync logic.
 */
@Entity(
    tableName = "playlists",
    indices = [Index(value = ["server_id"])]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    var playlistId: Long = 0, // Local DB primary key, auto-generated

    // Server-side fields
    @ColumnInfo(name = "server_id")
    var serverId: String?, // The UUID from the server, nullable for local-only playlists

    var name: String?,
    var description: String?,
    var owner: Long?,
    var type: String = "ugp",

    @ColumnInfo(name = "created_at")
    var createdAt: String?,

    @ColumnInfo(name = "updated_at")
    var last_modified_at: String?, // Cleaned up property name to match column

    // Client-side sync state fields, ignored by network serialization
    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    var isDeleted: Boolean = false,

    @ColumnInfo(name = "sync_status", defaultValue = "'DIRTY'")
    var syncStatus: SyncStatus = SyncStatus.DIRTY
)
// --- END REPLACEMENT ---


@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlist_owner_id", "item_id_in_playlist"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlist_owner_id"],
            onDelete = ForeignKey.CASCADE
        )

    ],
    indices = [Index(value = ["playlist_owner_id"])]
)
data class PlaylistItemEntity(
    @ColumnInfo(name = "playlist_owner_id") val playlistOwnerId: Long,
    @ColumnInfo(name = "item_id_in_playlist") val itemIdInPlaylist: String, // Mirrors structure of LikedItemEntity.itemId
    @ColumnInfo(name = "video_id_for_item") val videoIdForItem: String, // Parent video ID
    @ColumnInfo(name = "item_type_in_playlist") val itemTypeInPlaylist: LikedItemType,
    @ColumnInfo(name = "is_local_only", defaultValue = "0") val isLocalOnly: Boolean = false,
    // Optional: Snapshot of data for quick display, similar to LikedItemEntity
    @ColumnInfo(name = "song_start_seconds_playlist") val songStartSecondsPlaylist: Int? = null,
    @ColumnInfo(name = "song_end_seconds_playlist") val songEndSecondsPlaylist: Int? = null,
    @ColumnInfo(name = "song_name_playlist") val songNamePlaylist: String? = null,
    @ColumnInfo(name = "song_artist_text_playlist") val songArtistTextPlaylist: String? = null,
    @ColumnInfo(name = "song_artwork_url_playlist") val songArtworkUrlPlaylist: String? = null,
    // Could also store video title snapshot if it's a video item

    @ColumnInfo(name = "added_at", defaultValue = "CURRENT_TIMESTAMP")
    val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "item_order") val itemOrder: Int, // Order of the item within this specific playlist
    @ColumnInfo(name = "last_modified_at", defaultValue = "0")
    val lastModifiedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status", defaultValue = "'DIRTY'")
    var syncStatus: SyncStatus = SyncStatus.DIRTY
)



@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val dataType: String, // e.g., "likes", "history"
    val lastSyncTimestamp: Long
)
@Entity(tableName = "parent_video_metadata")
data class ParentVideoMetadataEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String?,
    val description: String?,
    val totalDurationSec: Long
)