package com.example.holodex.viewmodel

import androidx.compose.runtime.Immutable
import com.example.holodex.data.db.LikedItemType

@Immutable
data class UnifiedDisplayItem(
    val stableId: String,

    /** The ID used for Player consistency (Composite ID: videoId_start) */
    val playbackItemId: String,

    /** The clean YouTube ID used for Navigation and API calls. */
    val navigationVideoId: String,

    /**
     * The ID of the parent video container.
     * For full videos, this is the same as navigationVideoId.
     * For segments, this is the ID of the video containing the segment.
     */
    val videoId: String,

    val channelId: String,

    val title: String,
    val artistText: String,
    val artworkUrls: List<String>,
    val durationText: String,

    val songCount: Int?,
    val isDownloaded: Boolean,

    val downloadStatus: String?,

    val localFilePath: String?,

    val isSegment: Boolean,
    val isLiked: Boolean,

    val itemTypeForPlaylist: LikedItemType,
    val songStartSec: Int?,
    val songEndSec: Int?,
    val originalArtist: String?,
    val isExternal: Boolean
)