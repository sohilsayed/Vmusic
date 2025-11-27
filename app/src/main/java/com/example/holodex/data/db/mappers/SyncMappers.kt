// File: java\com\example\holodex\data\db\mappers\SyncMappers.kt (NEW FILE)
package com.example.holodex.data.db.mappers

import com.example.holodex.data.api.PlaylistDto
import com.example.holodex.data.db.HistoryItemEntity
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.discovery.MusicdexSong
import timber.log.Timber


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