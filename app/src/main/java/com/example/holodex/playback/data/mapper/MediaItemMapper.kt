// File: java/com/example/holodex/playback/data/mapper/MediaItemMapper.kt
package com.example.holodex.playback.data.mapper

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.holodex.R
import com.example.holodex.data.AppPreferenceConstants
import com.example.holodex.playback.domain.model.PersistedPlaybackItem
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.util.getHighResArtworkUrl
import timber.log.Timber
import java.util.concurrent.TimeUnit

internal const val EXTRA_KEY_HOLODEX_VIDEO_ID = "com.example.holodex.EXTRA_VIDEO_ID"
internal const val EXTRA_KEY_HOLODEX_SONG_ID = "com.example.holodex.EXTRA_SONG_ID"
internal const val EXTRA_KEY_HOLODEX_SERVER_UUID = "com.example.holodex.EXTRA_SERVER_UUID"
internal const val EXTRA_KEY_ORIGINAL_DURATION_SEC = "com.example.holodex.EXTRA_DURATION_SEC"
internal const val EXTRA_KEY_ARTIST_TEXT = "com.example.holodex.EXTRA_ARTIST_TEXT"
internal const val EXTRA_KEY_ALBUM_TEXT = "com.example.holodex.EXTRA_ALBUM_TEXT"
internal const val EXTRA_KEY_DESCRIPTION_TEXT = "com.example.holodex.EXTRA_DESCRIPTION_TEXT"
internal const val EXTRA_KEY_HOLODEX_CHANNEL_ID = "com.example.holodex.EXTRA_CHANNEL_ID"
private const val MAPPER_TAG = "MediaItemMapper"
private val PLACEHOLDER_URI = "placeholder://unresolved".toUri()


class MediaItemMapper(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    fun toMedia3MediaItem(playbackItem: PlaybackItem): MediaItem { // Removed nullable return type (?)
        Timber.tag(MAPPER_TAG).d("toMedia3MediaItem: Mapping PlaybackItem ID '${playbackItem.id}'")

        // --- FIX START ---
        // Instead of returning null, we determine the URI.
        // If it exists (e.g. downloaded file), use it.
        // If not, use the custom scheme to trigger Just-In-Time resolution.
        val uriToUse = if (!playbackItem.streamUri.isNullOrBlank()) {
            playbackItem.streamUri!!.toUri()
        } else {
            // This triggers HolodexResolvingDataSource
            "holodex://resolve/${playbackItem.videoId}".toUri()
        }
        // --- FIX END ---

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

        val imageQualityPref = sharedPreferences.getString(AppPreferenceConstants.PREF_IMAGE_QUALITY, AppPreferenceConstants.IMAGE_QUALITY_AUTO)
            ?: AppPreferenceConstants.IMAGE_QUALITY_AUTO
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
            .setUri(uriToUse) // Use the determined URI

        val isLocalFile = uriToUse.toString().startsWith("content://") || uriToUse.toString().startsWith("file://")

        if (!isLocalFile && playbackItem.clipStartSec != null && playbackItem.clipEndSec != null && playbackItem.clipEndSec > playbackItem.clipStartSec) {
            // This is a stream (or resolved stream), apply clipping.
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(playbackItem.clipStartSec * 1000L)
                    .setEndPositionMs(playbackItem.clipEndSec * 1000L)
                    .setRelativeToDefaultPosition(false)
                    .build()
            )
            Timber.d("Applied STREAM clipping to MediaItem ${playbackItem.id}: Start: ${playbackItem.clipStartSec}s, End: ${playbackItem.clipEndSec}s")
        } else {
            Timber.d("Skipping clipping for MediaItem ${playbackItem.id}. Is local file: $isLocalFile")
        }

        return mediaItemBuilder.build()
    }

    fun toPlaceholderMediaItem(playbackItem: PlaybackItem): MediaItem {
        Timber.tag(MAPPER_TAG).d("toPlaceholderMediaItem: Creating placeholder for '${playbackItem.id}'")

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

        val imageQualityPref = sharedPreferences.getString(AppPreferenceConstants.PREF_IMAGE_QUALITY, AppPreferenceConstants.IMAGE_QUALITY_AUTO)
            ?: AppPreferenceConstants.IMAGE_QUALITY_AUTO
        val highResArtworkUriString = getHighResArtworkUrl(playbackItem.artworkUri, imageQualityKey = imageQualityPref)

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(playbackItem.title.ifBlank { context.getString(R.string.unknown_title) })
            .setArtist(playbackItem.artistText.ifBlank { context.getString(R.string.unknown_artist) })
            .setAlbumTitle(playbackItem.albumText ?: playbackItem.title)
            .setArtworkUri(highResArtworkUriString?.toUri())
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(playbackItem.id)
            .setMediaMetadata(mediaMetadata)
            .setUri(PLACEHOLDER_URI)
            .build()
    }

    fun toPlaybackItem(mediaItem: MediaItem): PlaybackItem? {
        val mediaId = mediaItem.mediaId
        if (mediaId.isBlank()) {
            Timber.w("Cannot convert MediaItem to PlaybackItem: mediaId is null or blank.")
            return null
        }

        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras ?: Bundle.EMPTY

        val durationSecFromExtras = extras.getLong(EXTRA_KEY_ORIGINAL_DURATION_SEC, -1L)

        val clipStartSecFromMediaItem =
            if (mediaItem.clippingConfiguration.startPositionMs != C.TIME_UNSET) {
                TimeUnit.MILLISECONDS.toSeconds(mediaItem.clippingConfiguration.startPositionMs)
            } else null
        val clipEndSecFromMediaItem =
            if (mediaItem.clippingConfiguration.endPositionMs != C.TIME_UNSET) {
                TimeUnit.MILLISECONDS.toSeconds(mediaItem.clippingConfiguration.endPositionMs)
            } else null

        val finalDurationSec = if (durationSecFromExtras >= 0) {
            durationSecFromExtras
        } else {
            Timber.w("MediaItem $mediaId: Could not determine duration from extras. Defaulting to 0.")
            0L
        }

        return PlaybackItem(
            id = mediaId,
            videoId = extras.getString(EXTRA_KEY_HOLODEX_VIDEO_ID) ?: "unknown_video_id",
            serverUuid = extras.getString(EXTRA_KEY_HOLODEX_SERVER_UUID),
            songId = extras.getString(EXTRA_KEY_HOLODEX_SONG_ID),
            title = metadata.title?.toString() ?: context.getString(R.string.unknown_title),
            artistText = extras.getString(EXTRA_KEY_ARTIST_TEXT) ?: metadata.artist?.toString()
            ?: context.getString(R.string.unknown_artist),
            albumText = extras.getString(EXTRA_KEY_ALBUM_TEXT) ?: metadata.albumTitle?.toString(),
            artworkUri = metadata.artworkUri?.toString(),
            durationSec = finalDurationSec,
            streamUri = mediaItem.localConfiguration?.uri?.toString(),
            clipStartSec = clipStartSecFromMediaItem,
            clipEndSec = clipEndSecFromMediaItem,
            description = extras.getString(EXTRA_KEY_DESCRIPTION_TEXT),
            channelId = extras.getString(EXTRA_KEY_HOLODEX_CHANNEL_ID) ?: "unknown_channel_id",
            originalArtist = null // This info is not typically passed through MediaItem
        )
    }

    fun toPersistedPlaybackItem(playbackItem: PlaybackItem): PersistedPlaybackItem {
        return PersistedPlaybackItem(
            id = playbackItem.id,
            videoId = playbackItem.videoId,
            songId = playbackItem.serverUuid, // Persist the serverUuid in the songId field
            title = playbackItem.title,
            artistText = playbackItem.artistText,
            albumText = playbackItem.albumText,
            artworkUri = playbackItem.artworkUri,
            durationSec = playbackItem.durationSec,
            description = playbackItem.description,
            channelId = playbackItem.channelId,
            clipStartSec = playbackItem.clipStartSec,
            clipEndSec = playbackItem.clipEndSec
        )
    }

    fun toPlaybackItem(persistedPlaybackItem: PersistedPlaybackItem): PlaybackItem {
        return PlaybackItem(
            id = persistedPlaybackItem.id,
            videoId = persistedPlaybackItem.videoId,
            serverUuid = persistedPlaybackItem.songId,
            songId = persistedPlaybackItem.songId,
            title = persistedPlaybackItem.title,
            artistText = persistedPlaybackItem.artistText,
            albumText = persistedPlaybackItem.albumText,
            artworkUri = persistedPlaybackItem.artworkUri,
            durationSec = persistedPlaybackItem.durationSec,
            streamUri = null,
            description = persistedPlaybackItem.description,
            channelId = persistedPlaybackItem.channelId,
            clipStartSec = persistedPlaybackItem.clipStartSec,
            clipEndSec = persistedPlaybackItem.clipEndSec,
            originalArtist = null
        )
    }
}
