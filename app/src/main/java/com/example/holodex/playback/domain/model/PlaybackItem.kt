// File: java/com/example/holodex/playback/domain/model/PlaybackItem.kt
package com.example.holodex.playback.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlaybackItem(
    /** The unique composite ID for this item, used for playback and UI state. (e.g., "videoId_startTime") */
    val id: String,

    /** The ID of the parent YouTube video. */
    val videoId: String,

    /** The server's unique UUID for this specific song segment. Null for full videos. */
    val serverUuid: String?,

    /** DEPRECATED USAGE: This was used ambiguously. Now primarily for internal reference if needed. */
    val songId: String?,

    val title: String,
    val artistText: String,
    val albumText: String?,
    val artworkUri: String?,
    val durationSec: Long,
    var streamUri: String? = null,
    val clipStartSec: Long? = null,
    val clipEndSec: Long? = null,
    val description: String? = null,
    val channelId: String,
    val originalArtist: String? = null,

    // *** THIS IS THE MISSING PROPERTY ***
    val isExternal: Boolean = false
) : Parcelable