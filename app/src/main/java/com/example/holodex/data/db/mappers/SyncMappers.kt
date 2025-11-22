// File: java\com\example\holodex\data\db\mappers\SyncMappers.kt (NEW FILE)
package com.example.holodex.data.db.mappers

import com.example.holodex.data.api.FavoriteChannelApiDto
import com.example.holodex.data.api.LikedSongApiDto
import com.example.holodex.data.api.PlaylistDto
import com.example.holodex.data.db.FavoriteChannelEntity
import com.example.holodex.data.db.HistoryItemEntity
import com.example.holodex.data.db.LikedItemEntity
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.discovery.MusicdexSong
import timber.log.Timber

fun LikedSongApiDto.toLikedItemEntity(parentVideo: HolodexVideoItem): LikedItemEntity {
    return LikedItemEntity(
        itemId = this.id,
        serverId = this.id,
        videoId = this.video_id,
        itemType = LikedItemType.SONG_SEGMENT,
        titleSnapshot = this.name,
        artistTextSnapshot = parentVideo.channel.name,
        albumTextSnapshot = parentVideo.title,
        artworkUrlSnapshot = this.channel?.photo ?: parentVideo.channel.photoUrl,
        descriptionSnapshot = parentVideo.description,
        channelIdSnapshot = this.channel_id,
        durationSecSnapshot = (this.end - this.start).toLong(),
        actualSongName = this.name,
        actualSongArtist = this.original_artist,
        actualSongArtworkUrl = null, // This specific art isn't in the liked response
        songStartSeconds = this.start,
        songEndSeconds = this.end,
        syncStatus = SyncStatus.SYNCED,
        lastModifiedAt = System.currentTimeMillis()
    )
}

fun FavoriteChannelApiDto.toFavoriteChannelEntity(): FavoriteChannelEntity {
    return FavoriteChannelEntity(
        id = this.id,
        name = this.name ?: this.english_name
        ?: "Unknown Channel",
        englishName = this.english_name,
        photoUrl = this.photo,
        org = this.org,
        subscriberCount = null,
        twitter = this.twitter,
        syncStatus = SyncStatus.SYNCED,
        isDeleted = false,
        favoritedAtTimestamp = System.currentTimeMillis()
    )
}


fun LikedSongApiDto.toLikedItemEntityShell(): LikedItemEntity {
    return LikedItemEntity(
        itemId = LikedItemEntity.generateSongItemId(this.video_id, this.start), // Local composite key
        serverId = this.id, // <-- SAVE THE SERVER UUID
        videoId = this.video_id,
        itemType = LikedItemType.SONG_SEGMENT,
        titleSnapshot = this.name,
        artistTextSnapshot = this.channel?.name ?: "Unknown Channel",
        albumTextSnapshot = "...", // Placeholder to be enriched
        artworkUrlSnapshot = this.art,
        descriptionSnapshot = null, // To be enriched
        channelIdSnapshot = this.channel_id,
        durationSecSnapshot = (this.end - this.start).toLong(),
        actualSongName = this.name,
        actualSongArtist = this.original_artist,
        actualSongArtworkUrl = this.art,
        songStartSeconds = this.start,
        songEndSeconds = this.end,
        syncStatus = SyncStatus.SYNCED, // Data comes from server, so it's synced
        lastModifiedAt = System.currentTimeMillis()
    )
}
fun MusicdexSong.toHistoryItemEntity(
    parentVideo: HolodexVideoItem,
    // --- START OF FIX: Add the new parameter ---
    syntheticPlayedAtTimestamp: Long
    // --- END OF FIX ---
): HistoryItemEntity? {
    // We no longer need to parse the 'ts' field as it doesn't exist.
    // We will use the provided synthetic timestamp directly.
    if (this.channelId.isNullOrBlank()) {
        Timber.w("Skipping history item ('${this.name}') because its top-level 'channel_id' is missing.")
        return null
    }

    return HistoryItemEntity(
        playedAtTimestamp = syntheticPlayedAtTimestamp, // <-- Use the passed-in value
        itemId = "${this.videoId}_${this.start}",
        videoId = this.videoId,
        songStartSeconds = this.start,
        title = this.name,
        artistText = this.channel.name,
        artworkUrl = this.artUrl,
        durationSec = (this.end - this.start).toLong(),
        channelId = this.channelId
    )
}
fun PlaylistDto.toEntity(): PlaylistEntity {
    return PlaylistEntity(
        playlistId = 0, // Let Room auto-generate the local ID
        serverId = this.id,
        name = this.title,
        description = this.description,
        owner = this.owner,
        type = this.type ?: "ugp",
        createdAt = this.createdAt,
        last_modified_at = this.updatedAt,
        isDeleted = false,
        syncStatus = SyncStatus.SYNCED // Data from server is considered synced
    )
}