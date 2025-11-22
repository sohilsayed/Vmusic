package com.example.holodex.viewmodel

import androidx.compose.runtime.Immutable
import com.example.holodex.data.db.LikedItemType

/**
 * A single, canonical data class for any item displayed in a list.
 * It contains all possible fields the UI might need, abstracting away the
 * original data source (video, liked item, history, download, etc.).
 * Marked as Immutable for Compose performance optimization.
 */
@Immutable
data class UnifiedDisplayItem(
    // Core Identifiers
    val stableId: String, // A unique ID for this item in a list (e.g., "history_12345", "liked_videoId_start")
    val playbackItemId: String, // The ID used for playback and liking (e.g., "videoId_start" or "videoId")
    val videoId: String,
    val channelId: String,

    // Display Fields
    val title: String,
    val artistText: String,
    val artworkUrls: List<String>, // A prioritized list of URLs for Coil to try
    val durationText: String,

    // Metadata & Badges
    val songCount: Int?, // For full videos, null for segments
    val isDownloaded: Boolean,
    val isSegment: Boolean,
    val isLiked: Boolean,

    // Data for Actions
    val itemTypeForPlaylist: LikedItemType, // Is it a VIDEO or a SONG_SEGMENT?
    val songStartSec: Int?,
    val songEndSec: Int?,
    val originalArtist: String?,
    val isExternal: Boolean
)