package com.example.holodex.viewmodel.mappers

import com.example.holodex.data.db.LikedItemType
import com.example.holodex.data.db.PlaylistItemEntity
import com.example.holodex.data.db.UnifiedItemProjection
import com.example.holodex.data.model.HolodexChannelMin
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.discovery.MusicdexSong
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.util.formatDurationSeconds
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.generateArtworkUrlList
import com.example.holodex.viewmodel.UnifiedDisplayItem
import timber.log.Timber
import kotlin.math.max

// ============================================================================================
// 1. DATABASE PROJECTION -> UI MODEL (The "Unified" Way)
// ============================================================================================

fun UnifiedItemProjection.toUnifiedDisplayItem(): UnifiedDisplayItem {
    val downloadInteraction = interactions.find { it.interactionType == "DOWNLOAD" }
    val likeInteraction = interactions.find { it.interactionType == "LIKE" }
    val isSegment = metadata.type == "SEGMENT"
    if (metadata.type == "CHANNEL") {
        Timber.d("MAPPER DEBUG: ID=${metadata.id}, Org=${metadata.org}, isExternal=${metadata.org == "External"}")
    }
    return UnifiedDisplayItem(
        stableId = "${metadata.type.lowercase()}_${metadata.id}",
        playbackItemId = metadata.id,
        videoId = metadata.parentVideoId ?: metadata.id,
        channelId = metadata.channelId,
        title = metadata.title,
        artistText = metadata.artistName,
        artworkUrls = metadata.getComputedArtworkList(),
        durationText = formatDurationSeconds(metadata.duration),
        isSegment = isSegment,
        songCount = if (isSegment) null else metadata.songCount,
        isDownloaded = downloadInteraction?.downloadStatus == "COMPLETED",
        downloadStatus = downloadInteraction?.downloadStatus,
        localFilePath = downloadInteraction?.localFilePath, // Fix: Use the new field
        isLiked = likeInteraction != null,
        itemTypeForPlaylist = if (isSegment) LikedItemType.SONG_SEGMENT else LikedItemType.VIDEO,
        songStartSec = metadata.startSeconds?.toInt(),
        songEndSec = metadata.endSeconds?.toInt(),
        originalArtist = null,
        isExternal = metadata.org == "External"
    )
}

// ============================================================================================
// 2. UI MODEL -> PLAYER MODEL
// ============================================================================================

fun UnifiedDisplayItem.toPlaybackItem(): PlaybackItem {
    val finalStreamUri = if (this.isDownloaded && !this.localFilePath.isNullOrBlank()) {
        this.localFilePath
    } else {
        null
    }

    return PlaybackItem(
        id = this.playbackItemId,
        videoId = this.videoId,
        serverUuid = if (this.isSegment) this.playbackItemId else null,
        songId = if (this.isSegment) this.playbackItemId else null,
        title = this.title,
        artistText = this.artistText,
        albumText = if (!this.isSegment) this.title else null,
        artworkUri = this.artworkUrls.firstOrNull(),
        durationSec = this.songEndSec?.toLong()?.let { it - (this.songStartSec?.toLong() ?: 0) } ?: 0L,
        streamUri = finalStreamUri,
        clipStartSec = this.songStartSec?.toLong(),
        clipEndSec = this.songEndSec?.toLong(),
        description = null,
        channelId = this.channelId,
        originalArtist = this.originalArtist,
        isExternal = this.isExternal
    )
}

// ============================================================================================
// 3. API/LEGACY MODELS -> UI MODEL
// ============================================================================================

fun HolodexVideoItem.toUnifiedDisplayItem(
    isLiked: Boolean,
    downloadedSegmentIds: Set<String>
): UnifiedDisplayItem {
    val containsDownloadedSegments = this.songs?.any { song ->
        val segmentId = "${this.id}_${song.start}"
        downloadedSegmentIds.contains(segmentId)
    } == true

    return UnifiedDisplayItem(
        stableId = "video_${this.id}",
        playbackItemId = this.id,
        videoId = this.id,
        channelId = this.channel.id ?: this.id,
        title = this.title,
        artistText = this.channel.name,
        artworkUrls = generateArtworkUrlList(this.toPlaybackItem(), ThumbnailQuality.MEDIUM),
        durationText = formatDurationSeconds(this.duration),
        isSegment = false,
        songCount = this.songcount,
        isDownloaded = containsDownloadedSegments,
        downloadStatus = if (containsDownloadedSegments) "COMPLETED" else null,
        localFilePath = null,
        isLiked = isLiked,
        itemTypeForPlaylist = LikedItemType.VIDEO,
        songStartSec = null,
        songEndSec = null,
        originalArtist = null,
        isExternal = this.channel.org == "External"
    )
}

fun HolodexSong.toUnifiedDisplayItem(
    parentVideo: HolodexVideoItem,
    isLiked: Boolean,
    isDownloaded: Boolean
): UnifiedDisplayItem {
    val playbackItemId = "${parentVideo.id}_${this.start}"
    return UnifiedDisplayItem(
        stableId = "song_${playbackItemId}",
        playbackItemId = playbackItemId,
        videoId = parentVideo.id,
        channelId = parentVideo.channel.id ?: parentVideo.id,
        title = this.name,
        artistText = parentVideo.channel.name,
        artworkUrls = generateArtworkUrlList(this.toPlaybackItem(parentVideo), ThumbnailQuality.MEDIUM),
        durationText = formatDurationSeconds((this.end - this.start).toLong()),
        isSegment = true,
        songCount = null,
        isDownloaded = isDownloaded,
        downloadStatus = if (isDownloaded) "COMPLETED" else null,
        localFilePath = null,
        isLiked = isLiked,
        itemTypeForPlaylist = LikedItemType.SONG_SEGMENT,
        songStartSec = this.start,
        songEndSec = this.end,
        originalArtist = this.originalArtist,
        isExternal = parentVideo.channel.org == "External"
    )
}

fun PlaylistItemEntity.toUnifiedDisplayItem(
    isDownloaded: Boolean,
    isLiked: Boolean
): UnifiedDisplayItem {
    val isSegment = this.itemTypeInPlaylist == LikedItemType.SONG_SEGMENT
    val durationSec = if (isSegment && this.songStartSecondsPlaylist != null && this.songEndSecondsPlaylist != null) {
        max(1, (this.songEndSecondsPlaylist - this.songStartSecondsPlaylist)).toLong()
    } else {
        0L
    }

    return UnifiedDisplayItem(
        stableId = "playlist_${this.playlistOwnerId}_${this.itemIdInPlaylist}",
        playbackItemId = this.itemIdInPlaylist,
        videoId = this.videoIdForItem,
        channelId = "",
        title = this.songNamePlaylist ?: "Unknown Title",
        artistText = this.songArtistTextPlaylist ?: "Unknown Artist",
        artworkUrls = listOfNotNull(this.songArtworkUrlPlaylist),
        durationText = formatDurationSeconds(durationSec),
        isSegment = isSegment,
        songCount = null,
        isDownloaded = isDownloaded,
        downloadStatus = if (isDownloaded) "COMPLETED" else null,
        localFilePath = null,
        isLiked = isLiked,
        itemTypeForPlaylist = this.itemTypeInPlaylist,
        songStartSec = this.songStartSecondsPlaylist,
        songEndSec = this.songEndSecondsPlaylist,
        originalArtist = this.songArtistTextPlaylist,
        isExternal = this.isLocalOnly
    )
}
fun MusicdexSong.toVideoShell(albumTitle: String = "Unknown Video"): HolodexVideoItem {
    return HolodexVideoItem(
        id = this.videoId,
        title = albumTitle,
        type = "stream",
        topicId = null,
        availableAt = "",
        publishedAt = null,
        duration = (this.end - this.start).toLong(),
        status = "past",
        channel = HolodexChannelMin(
            id = this.channel.id ?: this.channelId,
            name = this.channel.name,
            englishName = this.channel.englishName,
            org = null,
            type = "vtuber",
            photoUrl = this.channel.photoUrl
        ),
        songcount = 1,
        description = null,
        songs = null
    )
}
fun MusicdexSong.toUnifiedDisplayItem(
    parentVideo: HolodexVideoItem,
    isLiked: Boolean,
    isDownloaded: Boolean
): UnifiedDisplayItem {
    val playbackItemId = "${this.videoId}_${this.start}"
    return UnifiedDisplayItem(
        stableId = "song_${playbackItemId}",
        playbackItemId = playbackItemId,
        videoId = this.videoId,
        channelId = this.channel.id ?: "",
        title = this.name,
        artistText = this.channel.name,
        artworkUrls = generateArtworkUrlList(this.toPlaybackItem(parentVideo), ThumbnailQuality.MEDIUM),
        durationText = formatDurationSeconds((this.end - this.start).toLong()),
        isSegment = true,
        songCount = null,
        isDownloaded = isDownloaded,
        downloadStatus = if (isDownloaded) "COMPLETED" else null,
        localFilePath = null,
        isLiked = isLiked,
        itemTypeForPlaylist = LikedItemType.SONG_SEGMENT,
        songStartSec = this.start,
        songEndSec = this.end,
        originalArtist = this.originalArtist,
        isExternal = parentVideo.channel.org == "External"
    )
}

fun HolodexVideoItem.toVirtualSegmentUnifiedDisplayItem(
    isLiked: Boolean,
    isDownloaded: Boolean
): UnifiedDisplayItem {
    val playbackItemId = "${this.id}_0"
    return UnifiedDisplayItem(
        stableId = "video_as_segment_${this.id}",
        playbackItemId = playbackItemId,
        videoId = this.id,
        channelId = this.channel.id ?: "",
        title = this.title,
        artistText = this.channel.name,
        artworkUrls = generateArtworkUrlList(this.toPlaybackItem(), ThumbnailQuality.MEDIUM),
        durationText = formatDurationSeconds(this.duration),
        isSegment = true,
        songCount = 0,
        isDownloaded = isDownloaded,
        downloadStatus = if (isDownloaded) "COMPLETED" else null,
        localFilePath = null,
        isLiked = isLiked,
        itemTypeForPlaylist = LikedItemType.VIDEO,
        songStartSec = 0,
        songEndSec = this.duration.toInt(),
        originalArtist = null,
        isExternal = this.channel.org == "External"
    )
}

// ============================================================================================
// 4. API MODEL -> PLAYER MODEL
// ============================================================================================

fun HolodexVideoItem.toPlaybackItem(): PlaybackItem {
    return PlaybackItem(
        id = this.id,
        videoId = this.id,
        serverUuid = null,
        songId = null,
        title = this.title,
        artistText = this.channel.name,
        albumText = this.title,
        artworkUri = this.channel.photoUrl,
        durationSec = this.duration,
        streamUri = null,
        clipStartSec = null,
        clipEndSec = null,
        description = this.description,
        channelId = this.channel.id ?: "unknown_channel_${this.id}",
        originalArtist = null,
        isExternal = this.channel.org == "External"
    )
}

fun HolodexSong.toPlaybackItem(parentVideo: HolodexVideoItem): PlaybackItem {
    val playbackId = "${parentVideo.id}_${this.start}"
    return PlaybackItem(
        id = playbackId,
        videoId = parentVideo.id,
        serverUuid = playbackId, // Placeholder until server ID is known
        songId = playbackId,
        title = this.name,
        artistText = parentVideo.channel.name,
        albumText = parentVideo.title,
        artworkUri = this.artUrl,
        durationSec = (this.end - this.start).toLong(),
        streamUri = null,
        clipStartSec = this.start.toLong(),
        clipEndSec = this.end.toLong(),
        description = parentVideo.description,
        channelId = parentVideo.channel.id ?: "unknown_channel_${parentVideo.id}",
        originalArtist = this.originalArtist,
        isExternal = parentVideo.channel.org == "External"
    )
}

fun MusicdexSong.toPlaybackItem(parentVideo: HolodexVideoItem): PlaybackItem {
    val playbackId = "${this.videoId}_${this.start}"
    return PlaybackItem(
        id = playbackId,
        videoId = parentVideo.id,
        serverUuid = this.id,
        songId = playbackId,
        title = this.name,
        artistText = parentVideo.channel.name,
        albumText = parentVideo.title,
        artworkUri = this.artUrl,
        durationSec = (this.end - this.start).toLong(),
        streamUri = null,
        clipStartSec = this.start.toLong(),
        clipEndSec = this.end.toLong(),
        description = parentVideo.description,
        channelId = parentVideo.channel.id ?: "unknown",
        originalArtist = this.originalArtist,
        isExternal = parentVideo.channel.org == "External"
    )
}