package com.example.holodex.playback.data.mapper

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.holodex.R
import com.example.holodex.data.AppPreferenceConstants
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.util.getHighResArtworkUrl
import javax.inject.Inject

// ... (Keep the const keys at the top of the file) ...
internal const val EXTRA_KEY_HOLODEX_VIDEO_ID = "com.example.holodex.EXTRA_VIDEO_ID"
internal const val EXTRA_KEY_HOLODEX_SONG_ID = "com.example.holodex.EXTRA_SONG_ID"
internal const val EXTRA_KEY_HOLODEX_SERVER_UUID = "com.example.holodex.EXTRA_SERVER_UUID"
internal const val EXTRA_KEY_ORIGINAL_DURATION_SEC = "com.example.holodex.EXTRA_DURATION_SEC"
internal const val EXTRA_KEY_ARTIST_TEXT = "com.example.holodex.EXTRA_ARTIST_TEXT"
internal const val EXTRA_KEY_ALBUM_TEXT = "com.example.holodex.EXTRA_ALBUM_TEXT"
internal const val EXTRA_KEY_DESCRIPTION_TEXT = "com.example.holodex.EXTRA_DESCRIPTION_TEXT"
internal const val EXTRA_KEY_HOLODEX_CHANNEL_ID = "com.example.holodex.EXTRA_CHANNEL_ID"
private const val MAPPER_TAG = "MediaItemMapper"

class MediaItemMapper @Inject constructor(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    fun toMedia3MediaItem(playbackItem: PlaybackItem): MediaItem {
        // 1. Determine URI
        // Controller has already populated streamUri if local.
        val uriToUse = if (!playbackItem.streamUri.isNullOrBlank()) {
            playbackItem.streamUri!!.toUri()
        } else {
            "holodex://resolve/${playbackItem.id}".toUri()
        }

        // 2. Build Metadata
        val extras = Bundle().apply {
            putString(EXTRA_KEY_HOLODEX_VIDEO_ID, playbackItem.videoId)
            playbackItem.songId?.let { putString(EXTRA_KEY_HOLODEX_SONG_ID, it) }
            playbackItem.serverUuid?.let { putString(EXTRA_KEY_HOLODEX_SERVER_UUID, it) }
            putLong(EXTRA_KEY_ORIGINAL_DURATION_SEC, playbackItem.durationSec)
            putString(EXTRA_KEY_ARTIST_TEXT, playbackItem.artistText)
            playbackItem.albumText?.let { putString(EXTRA_KEY_ALBUM_TEXT, it) }
            playbackItem.description?.let { putString(EXTRA_KEY_DESCRIPTION_TEXT, it) }
            putString(EXTRA_KEY_HOLODEX_CHANNEL_ID, playbackItem.channelId)
        }

        val imageQualityPref = sharedPreferences.getString(
            AppPreferenceConstants.PREF_IMAGE_QUALITY,
            AppPreferenceConstants.IMAGE_QUALITY_AUTO
        ) ?: AppPreferenceConstants.IMAGE_QUALITY_AUTO

        val highResArtworkUriString = getHighResArtworkUrl(playbackItem.artworkUri, imageQualityKey = imageQualityPref)

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(playbackItem.title.ifBlank { context.getString(R.string.unknown_title) })
            .setArtist(playbackItem.artistText.ifBlank { context.getString(R.string.unknown_artist) })
            .setAlbumTitle(playbackItem.albumText ?: playbackItem.title)
            .setArtworkUri(highResArtworkUriString?.toUri())
            .setExtras(extras)
            .build()

        val mediaItemBuilder = MediaItem.Builder()
            .setMediaId(playbackItem.id)
            .setMediaMetadata(mediaMetadata)
            .setUri(uriToUse)

        // 3. Apply Clipping
        // Controller clears clip start/end if file is local.
        // So we simply check if clip params exist. Simpler logic.
        if (playbackItem.clipStartSec != null && playbackItem.clipEndSec != null && playbackItem.clipEndSec > playbackItem.clipStartSec) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(playbackItem.clipStartSec * 1000L)
                    .setEndPositionMs(playbackItem.clipEndSec * 1000L)
                    .setRelativeToDefaultPosition(false)
                    .build()
            )
        }

        return mediaItemBuilder.build()
    }

    fun toPlaybackItem(mediaItem: MediaItem): PlaybackItem? {
        val mediaId = mediaItem.mediaId
        if (mediaId.isBlank()) return null

        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras ?: Bundle.EMPTY
        val durationSec = extras.getLong(EXTRA_KEY_ORIGINAL_DURATION_SEC, 0L)

        // Note: We don't recover clipping config here because the UI uses the ID to look up metadata anyway.

        return PlaybackItem(
            id = mediaId,
            videoId = extras.getString(EXTRA_KEY_HOLODEX_VIDEO_ID) ?: "unknown",
            serverUuid = extras.getString(EXTRA_KEY_HOLODEX_SERVER_UUID),
            songId = extras.getString(EXTRA_KEY_HOLODEX_SONG_ID),
            title = metadata.title?.toString() ?: "Unknown",
            artistText = extras.getString(EXTRA_KEY_ARTIST_TEXT) ?: metadata.artist?.toString() ?: "Unknown",
            albumText = extras.getString(EXTRA_KEY_ALBUM_TEXT),
            artworkUri = metadata.artworkUri?.toString(),
            durationSec = durationSec,
            streamUri = mediaItem.localConfiguration?.uri?.toString(),
            clipStartSec = null,
            clipEndSec = null,
            description = extras.getString(EXTRA_KEY_DESCRIPTION_TEXT),
            channelId = extras.getString(EXTRA_KEY_HOLODEX_CHANNEL_ID) ?: "unknown",
            originalArtist = null
        )
    }

}