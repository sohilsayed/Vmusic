// File: java/com/example/holodex/playback/util/PlaybackUtil.kt
package com.example.holodex.playback.util

import androidx.media3.common.Player
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Returns a human-readable name for the given playback state. */
fun playbackStateToString(state: Int): String = when (state) {
    Player.STATE_IDLE -> "STATE_IDLE"
    Player.STATE_BUFFERING -> "STATE_BUFFERING"
    Player.STATE_READY -> "STATE_READY"
    Player.STATE_ENDED -> "STATE_ENDED"
    else -> "UNKNOWN_PLAYBACK_STATE($state)"
}

/** Returns a readable name for media-item transition reasons. */
fun mediaItemTransitionReasonToString(reason: Int): String = when (reason) {
    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT_MODE"
    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
    else -> "UNKNOWN_TRANSITION_REASON($reason)"
}

/** Returns a readable name for timeline-change reasons. */
fun timelineChangeReasonToString(reason: Int): String = when (reason) {
    Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
    Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE -> "SOURCE_UPDATE"
    else -> "UNKNOWN_TIMELINE_REASON($reason)"
}

/** Returns a readable name for discontinuity reasons. */
fun discontinuityReasonToString(reason: Int): String = when (reason) {
    Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
    Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
    Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
    Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
    Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
    else -> "UNKNOWN_DISCONTINUITY_REASON($reason)"
}

/** Formats a duration in seconds into a MM:SS string. */
fun formatSongTimestamp(seconds: Long): String {
    if (seconds < 0) return "--:--"
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, secs)
}

/** Formats a total duration in seconds into a HH:MM:SS or MM:SS string. */
fun formatDurationSecondsToString(totalSeconds: Long): String {
    if (totalSeconds < 0) return "--:--"
    if (totalSeconds == 0L) return "00:00"

    val hours = TimeUnit.SECONDS.toHours(totalSeconds)
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val secs = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}

/** Formats a total duration in seconds into a HH:MM:SS or MM:SS string. */
fun formatDurationSeconds(totalSecondsLong: Long): String {
    if (totalSecondsLong <= 0) return ""
    val hours = TimeUnit.SECONDS.toHours(totalSecondsLong)
    val minutes = TimeUnit.SECONDS.toMinutes(totalSecondsLong) % 60
    val secs = totalSecondsLong % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}