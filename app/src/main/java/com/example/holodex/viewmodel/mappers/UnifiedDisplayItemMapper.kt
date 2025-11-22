// File: java/com/example/holodex/viewmodel/mappers/UnifiedDisplayItemMapper.kt
package com.example.holodex.viewmodel.mappers

import com.example.holodex.data.db.DownloadedItemEntity
import com.example.holodex.data.db.HistoryItemEntity
import com.example.holodex.data.db.LikedItemEntity
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.data.db.PlaylistItemEntity
import com.example.holodex.data.model.HolodexChannelMin
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.discovery.MusicdexSong
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.util.formatDurationSeconds
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.generateArtworkUrlList
import com.example.holodex.viewmodel.UnifiedDisplayItem
import kotlin.math.max

// Mappers to UnifiedDisplayItem

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
        channelId = "", // This info is not stored in PlaylistItemEntity, might need to be added if required
        title = this.songNamePlaylist ?: "Unknown Title",
        artistText = this.songArtistTextPlaylist ?: "Unknown Artist",
        artworkUrls = listOfNotNull(this.songArtworkUrlPlaylist),
        durationText = formatDurationSeconds(durationSec),
        isSegment = isSegment,
        songCount = null,
        isDownloaded = isDownloaded,
        isLiked = isLiked,
        itemTypeForPlaylist = this.itemTypeInPlaylist,
        songStartSec = this.songStartSecondsPlaylist,
        songEndSec = this.songEndSecondsPlaylist,
        originalArtist = this.songArtistTextPlaylist,
        isExternal = this.isLocalOnly
    )
}

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
        isLiked = isLiked,
        itemTypeForPlaylist = LikedItemType.SONG_SEGMENT,
        songStartSec = this.start,
        songEndSec = this.end,
        originalArtist = this.originalArtist,
        isExternal = parentVideo.channel.org == "External"
    )
}

fun LikedItemEntity.toUnifiedDisplayItem(isDownloaded: Boolean): UnifiedDisplayItem {
    val isSegment = this.itemType == LikedItemType.SONG_SEGMENT
    return UnifiedDisplayItem(
        stableId = "liked_${this.itemId}",
        playbackItemId = this.itemId,
        videoId = this.videoId,
        channelId = this.channelIdSnapshot,
        title = if (isSegment) this.actualSongName ?: this.titleSnapshot else this.titleSnapshot,
        artistText = if (isSegment) this.actualSongArtist ?: this.artistTextSnapshot else this.artistTextSnapshot,
        artworkUrls = generateArtworkUrlList(this.toPlaybackItem(), ThumbnailQuality.MEDIUM),
        durationText = formatDurationSeconds(this.durationSecSnapshot),
        isSegment = isSegment,
        songCount = null,
        isDownloaded = isDownloaded,
        isLiked = true,
        itemTypeForPlaylist = this.itemType,
        songStartSec = this.songStartSeconds,
        songEndSec = this.songEndSeconds,
        originalArtist = this.actualSongArtist,
        isExternal = false
    )
}

fun HistoryItemEntity.toUnifiedDisplayItem(
    isDownloaded: Boolean,
    isLiked: Boolean
): UnifiedDisplayItem {
    return UnifiedDisplayItem(
        stableId = "history_${this.playedAtTimestamp}",
        playbackItemId = this.itemId,
        videoId = this.videoId,
        channelId = this.channelId,
        title = this.title,
        artistText = this.artistText,
        artworkUrls = generateArtworkUrlList(this.toPlaybackItem(), ThumbnailQuality.MEDIUM),
        durationText = formatDurationSeconds(this.durationSec),
        isSegment = true,
        songCount = null,
        isDownloaded = isDownloaded,
        isLiked = isLiked,
        itemTypeForPlaylist = LikedItemType.SONG_SEGMENT,
        songStartSec = this.songStartSeconds,
        songEndSec = (this.songStartSeconds + this.durationSec).toInt(),
        originalArtist = null,
        isExternal = false
    )
}

fun DownloadedItemEntity.toUnifiedDisplayItem(isLiked: Boolean): UnifiedDisplayItem {
    val parentVideoId = this.videoId.substringBeforeLast('_')
    val songStartSec = this.videoId.substringAfterLast('_').toIntOrNull()

    return UnifiedDisplayItem(
        stableId = "download_${this.videoId}",
        playbackItemId = this.videoId,
        videoId = parentVideoId,
        channelId = this.channelId,
        title = this.title,
        artistText = this.artistText,
        artworkUrls = generateArtworkUrlList(this.toPlaybackItem(), ThumbnailQuality.MEDIUM),
        durationText = formatDurationSeconds(this.durationSec),
        isSegment = true,
        songCount = null,
        isDownloaded = true,
        isLiked = isLiked,
        itemTypeForPlaylist = LikedItemType.SONG_SEGMENT,
        songStartSec = songStartSec,
        songEndSec = songStartSec?.let { it + this.durationSec.toInt() },
        originalArtist = this.artistText,
        isExternal = false
    )
}

// Mappers to PlaybackItem

fun UnifiedDisplayItem.toPlaybackItem(): PlaybackItem {
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
        streamUri = null,
        clipStartSec = this.songStartSec?.toLong(),
        clipEndSec = this.songEndSec?.toLong(),
        description = null,
        channelId = this.channelId,
        originalArtist = this.originalArtist,
        isExternal = this.isExternal
    )
}

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

internal fun HolodexSong.toPlaybackItem(parentVideo: HolodexVideoItem): PlaybackItem {
    val playbackId = "${parentVideo.id}_${this.start}"
    return PlaybackItem(
        id = playbackId,
        videoId = parentVideo.id,
        serverUuid = playbackId,
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

internal fun LikedItemEntity.toPlaybackItem(): PlaybackItem {
    return PlaybackItem(
        id = this.itemId,
        videoId = this.videoId,
        serverUuid = this.serverId,
        songId = if (this.itemType == LikedItemType.SONG_SEGMENT) this.itemId else null,
        title = this.titleSnapshot,
        artistText = this.artistTextSnapshot,
        albumText = this.albumTextSnapshot,
        artworkUri = this.artworkUrlSnapshot,
        durationSec = this.durationSecSnapshot,
        streamUri = null,
        clipStartSec = this.songStartSeconds?.toLong(),
        clipEndSec = this.songEndSeconds?.toLong(),
        description = this.descriptionSnapshot,
        channelId = this.channelIdSnapshot,
        originalArtist = this.actualSongArtist,
        isExternal = false
    )
}

internal fun HistoryItemEntity.toPlaybackItem(): PlaybackItem {
    return PlaybackItem(
        id = this.itemId,
        videoId = this.videoId,
        serverUuid = null,
        songId = this.itemId,
        title = this.title,
        artistText = this.artistText,
        albumText = this.title,
        artworkUri = this.artworkUrl,
        durationSec = this.durationSec,
        streamUri = null,
        clipStartSec = this.songStartSeconds.toLong(),
        clipEndSec = (this.songStartSeconds + this.durationSec),
        description = null,
        channelId = this.channelId,
        originalArtist = null,
        isExternal = false
    )
}

internal fun DownloadedItemEntity.toPlaybackItem(): PlaybackItem {
    val parentVideoId = this.videoId.substringBeforeLast('_')
    val songStartSec = this.videoId.substringAfterLast('_').toLongOrNull()

    return PlaybackItem(
        id = this.videoId,
        videoId = parentVideoId,
        serverUuid = this.videoId,
        songId = this.videoId,
        title = this.title,
        artistText = this.artistText,
        albumText = this.title,
        artworkUri = this.artworkUrl,
        durationSec = this.durationSec,
        streamUri = this.localFileUri,
        clipStartSec = songStartSec,
        clipEndSec = songStartSec?.let { it + this.durationSec },
        description = null,
        channelId = this.channelId,
        originalArtist = this.artistText,
        isExternal = false
    )
}

internal fun MusicdexSong.toVideoShell(): HolodexVideoItem {
    return HolodexVideoItem(
        id = this.videoId, title = "Unknown Video", type = "stream", topicId = null,
        availableAt = "", publishedAt = null, duration = (this.end - this.start).toLong(),
        status = "past",
        channel = HolodexChannelMin(
            id = this.channel.id ?: this.channelId, name = this.channel.name,
            englishName = this.channel.englishName, org = null, type = "vtuber",
            photoUrl = this.channel.photoUrl
        ),
        songcount = 1, description = null, songs = null
    )
}
internal fun MusicdexSong.toVideoShell(albumTitle: String): HolodexVideoItem {
    return this.toVideoShell().copy(title = albumTitle)
}
fun MusicdexSong.toUnifiedDisplayItem(
    parentVideo: HolodexVideoItem,
    isLiked: Boolean,
    isDownloaded: Boolean
): UnifiedDisplayItem {
    val playbackItemId = "${this.videoId}_${this.start}"
    return UnifiedDisplayItem(
        stableId = "song_${playbackItemId}", playbackItemId = playbackItemId, videoId = this.videoId,
        channelId = this.channel.id ?: "", title = this.name, artistText = this.channel.name,
        artworkUrls = generateArtworkUrlList(this.toPlaybackItem(parentVideo), ThumbnailQuality.MEDIUM),
        durationText = formatDurationSeconds((this.end - this.start).toLong()),
        isSegment = true, songCount = null, isDownloaded = isDownloaded, isLiked = isLiked,
        itemTypeForPlaylist = LikedItemType.SONG_SEGMENT, songStartSec = this.start,
        songEndSec = this.end, originalArtist = this.originalArtist,
        isExternal = parentVideo.channel.org == "External"
    )
}

internal fun MusicdexSong.toPlaybackItem(parentVideo: HolodexVideoItem): PlaybackItem {
    val reliableChannelId = this.channelId ?: parentVideo.channel.id ?: this.videoId
    val playbackId = "${this.videoId}_${this.start}"

    return PlaybackItem(
        id = playbackId,
        videoId = this.videoId,
        serverUuid = this.id,
        songId = playbackId,
        title = this.name,
        artistText = this.channel.name,
        albumText = parentVideo.title,
        artworkUri = this.artUrl,
        durationSec = (this.end - this.start).toLong(),
        streamUri = null,
        clipStartSec = this.start.toLong(),
        clipEndSec = this.end.toLong(),
        description = null,
        channelId = reliableChannelId,
        originalArtist = this.originalArtist,
        isExternal = false
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
        isLiked = isLiked,
        itemTypeForPlaylist = LikedItemType.VIDEO,
        songStartSec = 0,
        songEndSec = this.duration.toInt(),
        originalArtist = null,
        isExternal = this.channel.org == "External"
    )
}