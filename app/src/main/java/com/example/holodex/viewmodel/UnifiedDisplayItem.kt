package com.example.holodex.viewmodel

import androidx.compose.runtime.Immutable
import com.example.holodex.data.db.LikedItemType

@Immutable
data class UnifiedDisplayItem(
    val stableId: String,
    val playbackItemId: String,
    val videoId: String,
    val channelId: String,

    val title: String,
    val artistText: String,
    val artworkUrls: List<String>,
    val durationText: String,

    val songCount: Int?,
    val isDownloaded: Boolean,

    val downloadStatus: String?, // "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"

    val localFilePath: String?,

    val isSegment: Boolean,
    val isLiked: Boolean,

    val itemTypeForPlaylist: LikedItemType,
    val songStartSec: Int?,
    val songEndSec: Int?,
    val originalArtist: String?,
    val isExternal: Boolean
)