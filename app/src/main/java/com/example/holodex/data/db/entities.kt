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


@Entity(tableName = "videos")
data class CachedVideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    @ColumnInfo(name = "topic_id") val topicId: String?,
    @ColumnInfo(name = "published_at") val publishedAt: String?,
    @ColumnInfo(name = "available_at") val availableAt: String,
    val duration: Long,
    val status: String,
    @ColumnInfo(name = "song_count") val songCount: Int?,
    val description: String?, // This field will store the potentially truncated description
    @Embedded(prefix = "channel_")
    val channel: HolodexChannelMin,
    @ColumnInfo(
        name = "fetched_at_ms",
        defaultValue = "0"
    ) val fetchedAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "list_query_key") val listQueryKey: String? = null,
    @ColumnInfo(
        name = "insertion_order",
        defaultValue = "0"
    ) val insertionOrder: Int = 0 // NEW FIELD
)

@Entity(
    tableName = "songs",
    primaryKeys = ["video_id", "start_time_seconds"],
    foreignKeys = [ForeignKey(
        entity = CachedVideoEntity::class,
        parentColumns = ["id"],
        childColumns = ["video_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["video_id"])]
)
data class CachedSongEntity(
    @ColumnInfo(name = "video_id") val videoId: String,
    val name: String,
    @ColumnInfo(name = "start_time_seconds") val startTimeSeconds: Int,
    @ColumnInfo(name = "end_time_seconds") val endTimeSeconds: Int,
    @ColumnInfo(name = "original_artist") val originalArtist: String?,
    @ColumnInfo(name = "itunes_id") val itunesId: Int?,
    @ColumnInfo(name = "art_url") val artUrl: String?
)


// --- Mappers ---

fun HolodexVideoItem.toEntity(
    queryKey: String? = null,
    currentTimestamp: Long = System.currentTimeMillis(),
    orgNameFromRequest: String? = null,
    insertionOrder: Int = 0 // NEW PARAMETER
): CachedVideoEntity {
    val effectiveOrg = orgNameFromRequest ?: this.channel.org

    // Truncate description to a reasonable length
    val maxDescriptionLength = 1000 // Max 1000 characters for DB storage
    var truncatedDescription = this.description
    if (this.description?.length ?: 0 > maxDescriptionLength) {
        // Ensure truncation doesn't break multi-byte characters if present, though it's less critical for plain text
        truncatedDescription = this.description!!.substring(0, maxDescriptionLength) + "..."
        Timber.tag("HoloVideoItem.toEntity")
            .w("Description for video ${this.id} was truncated from ${this.description.length} to ${truncatedDescription.length} chars for DB storage.")
    }

    return CachedVideoEntity(
        id = this.id,
        title = this.title,
        type = this.type,
        topicId = this.topicId,
        publishedAt = this.publishedAt,
        availableAt = this.availableAt,
        duration = this.duration,
        status = this.status,
        songCount = this.songcount,
        description = truncatedDescription, // Use the (potentially) truncated description
        channel = HolodexChannelMin(
            id = this.channel.id,
            name = this.channel.name,
            englishName = this.channel.englishName,
            photoUrl = this.channel.photoUrl,
            type = this.channel.type,
            org = effectiveOrg
        ),
        fetchedAtMs = currentTimestamp,
        listQueryKey = queryKey,
        insertionOrder = insertionOrder // Populate new field
    )
}

fun HolodexSong.toEntity(videoIdParam: String): CachedSongEntity {
    return CachedSongEntity(
        videoId = videoIdParam,
        name = this.name,
        startTimeSeconds = this.start,
        endTimeSeconds = this.end,
        originalArtist = null, // This field was not in HolodexSong, keep as null or populate if data exists
        itunesId = this.itunesId,
        artUrl = this.artUrl
    )
}

fun CachedVideoEntity.toDomain(songsList: List<HolodexSong>? = null): HolodexVideoItem {
    return HolodexVideoItem(
        id = this.id,
        title = this.title,
        type = this.type,
        topicId = this.topicId,
        publishedAt = this.publishedAt,
        availableAt = this.availableAt,
        duration = this.duration,
        status = this.status,
        songcount = this.songCount,
        description = this.description, // This will be the (potentially) truncated description from DB
        channel = this.channel,
        songs = songsList
    )
}

fun CachedSongEntity.toDomain(): HolodexSong {
    return HolodexSong(
        name = this.name,
        start = this.startTimeSeconds,
        end = this.endTimeSeconds,
        itunesId = this.itunesId,
        videoId = this.videoId, // Ensure this is mapped back
        artUrl = this.artUrl
    )
}

data class VideoWithSongs(
    @Embedded val video: CachedVideoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "video_id"
    )
    val songs: List<CachedSongEntity>
) {
    fun toDomain(): HolodexVideoItem {
        return video.toDomain(songsList = songs.map { it.toDomain() })
    }
}







// --- START REPLACEMENT ---
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