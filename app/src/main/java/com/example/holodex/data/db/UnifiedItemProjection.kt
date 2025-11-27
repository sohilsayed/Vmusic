package com.example.holodex.data.db

import androidx.room.Embedded
import androidx.room.Relation
import com.example.holodex.viewmodel.UnifiedDisplayItem

data class UnifiedItemProjection(
    @Embedded val metadata: UnifiedMetadataEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val interactions: List<UserInteractionEntity>
) {
    fun toUnifiedDisplayItem(): UnifiedDisplayItem {
        val downloadInteraction = interactions.find { it.interactionType == "DOWNLOAD" }
        val likeInteraction = interactions.find { it.interactionType == "LIKE" }

        val isSegment = metadata.type == "SEGMENT"

        return UnifiedDisplayItem(
            stableId = "${metadata.type.lowercase()}_${metadata.id}",
            playbackItemId = metadata.id,
            videoId = metadata.parentVideoId ?: metadata.id,
            channelId = metadata.channelId,
            title = metadata.title,
            artistText = metadata.artistName,
            artworkUrls = metadata.getComputedArtworkList(),
            durationText = com.example.holodex.playback.util.formatDurationSeconds(metadata.duration),

            isSegment = isSegment,
            songCount = if(isSegment) null else metadata.songCount,

            isDownloaded = downloadInteraction?.downloadStatus == "COMPLETED",

            downloadStatus = downloadInteraction?.downloadStatus,

            isLiked = likeInteraction != null,

            itemTypeForPlaylist = if (isSegment) LikedItemType.SONG_SEGMENT else LikedItemType.VIDEO,
            songStartSec = metadata.startSeconds?.toInt(),
            songEndSec = metadata.endSeconds?.toInt(),
            originalArtist = null,
            isExternal = metadata.type == "CHANNEL" || metadata.org == "External"
        )
    }
}