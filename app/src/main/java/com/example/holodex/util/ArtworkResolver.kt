package com.example.holodex.util

import com.example.holodex.data.model.discovery.PlaylistStub
import timber.log.Timber
import java.util.regex.Pattern

/**
 * A utility object to resolve the best possible artwork URL for different data models.
 */
object ArtworkResolver {

    // Regex to extract channel ID from playlist IDs like ":dailyrandom[ch=...]" or ":artist[ch=...]"
    private val CHANNEL_ID_PATTERN: Pattern = Pattern.compile("ch=([a-zA-Z0-9_-]{24})")

    // --- START OF IMPLEMENTATION ---
    /**
     * Constructs the standard URL for a channel's photo based on its ID.
     * This is used as a fallback when the API does not provide a direct photo URL.
     *
     * @param channelId The unique ID of the channel.
     * @return The fully-formed URL to the channel's 200px profile picture.
     */
    fun getChannelPhotoUrl(channelId: String): String {
        return "https://holodex.net/statics/channelImg/$channelId/200.png"
    }
    // --- END OF IMPLEMENTATION ---

    /**
     * The main function to resolve playlist artwork. It follows the fallback logic
     * discovered from the Musicdex frontend.
     *
     * @param playlist The PlaylistStub object from the API.
     * @return The best available URL string for the playlist's artwork, or null if none can be determined.
     */
    fun getPlaylistArtworkUrl(playlist: PlaylistStub): String? {
        Timber.d("Resolving artwork for playlist: ${playlist.title} (Type: ${playlist.type})")

        // Method 1: Use the pre-defined channel image for specific types
        if (playlist.type.startsWith(":dailyrandom") || playlist.type.startsWith(":artist")) {
            val matcher = CHANNEL_ID_PATTERN.matcher(playlist.id)
            if (matcher.find()) {
                val channelId = matcher.group(1)
                if (!channelId.isNullOrBlank()) {
                    Timber.d("PlaylistArtwork: Using Method 1 (Channel ID from playlist ID)")
                    return "https://holodex.net/statics/channelImg/$channelId/200.png"
                }
            }
        }

        // Method 2: Use the art_context field
        val artContext = playlist.artContext
        if (artContext != null) {
            // Rule from Musicdex: If it seems channel-focused, use the channel image.
            val isChannelFocused = (artContext.channels?.size ?: 0) < 3 && (artContext.videos?.size ?: 0) > 1
            if (isChannelFocused && !artContext.channels.isNullOrEmpty()) {
                Timber.d("PlaylistArtwork: Using Method 2 (Channel-focused art_context)")
                return "https://holodex.net/statics/channelImg/${artContext.channels.first()}/200.png"
            }
            // Otherwise, assume it's video-focused.
            if (!artContext.videos.isNullOrEmpty()) {
                Timber.d("PlaylistArtwork: Using Method 2 (Video-focused art_context)")
                return getYouTubeThumbnailUrl(artContext.videos.first(), ThumbnailQuality.HIGH).firstOrNull()
            }
            // Fallback within art_context to channel photo if no videos
            if (!artContext.channelPhotoUrl.isNullOrBlank()) {
                Timber.d("PlaylistArtwork: Using Method 2 (Fallback channel photo from art_context)")
                return artContext.channelPhotoUrl
            }
        }

        Timber.w("PlaylistArtwork: No suitable URL found for playlist '${playlist.title}' using Methods 1 or 2.")
        return null
    }
}