// File: java/com/example/holodex/util/ImageUtils.kt
package com.example.holodex.util // Or your preferred util package

import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.viewmodel.AppPreferenceConstants
import timber.log.Timber

// Regex to find the size part of an iTunes/mzstatic image URL
private val ITUNES_ARTWORK_SIZE_REGEX = Regex("""/(\d+x\d+)(bb)?\.jpg$""")

// Default sizes remain, but will be overridden by preference
private const val DEFAULT_HIGH_RES_SIZE = "600x600"
private const val MEDIUM_RES_SIZE = "300x300"
private const val LOW_RES_SIZE = "150x150"

enum class ThumbnailQuality {
    LOW,    // For tiny notifications or widgets (default.jpg - 120x90)
    MEDIUM, // For list items (mqdefault.jpg - 320x180)
    HIGH,   // For larger cards or mini-player (hqdefault.jpg - 480x360)
    MAX     // For full-screen displays (maxresdefault.jpg - 1280x720)
}

/**
 * NEW: Generates a prioritized list of YouTube thumbnail URLs for a given video ID.
 * Coil will attempt to load them in the order provided.
 */
fun getYouTubeThumbnailUrl(videoId: String, quality: ThumbnailQuality): List<String> {
    val baseUrl = "https://i.ytimg.com/vi/$videoId"
    return when (quality) {
        ThumbnailQuality.MAX -> listOf(
            "$baseUrl/maxresdefault.jpg",
            "$baseUrl/sddefault.jpg",
            "$baseUrl/hqdefault.jpg"
        )
        ThumbnailQuality.HIGH -> listOf(
            "$baseUrl/hqdefault.jpg",
            "$baseUrl/mqdefault.jpg",
            "$baseUrl/sddefault.jpg" // sddefault is often better than mqdefault
        )
        ThumbnailQuality.MEDIUM -> listOf(
            "$baseUrl/mqdefault.jpg",
            "$baseUrl/default.jpg"
        )
        ThumbnailQuality.LOW -> listOf(
            "$baseUrl/default.jpg"
        )
    }
}


// Updated function to take imageQualityKey as a parameter
fun getHighResArtworkUrl(
    originalUrl: String?,
    imageQualityKey: String = AppPreferenceConstants.IMAGE_QUALITY_AUTO, // Default to AUTO
    preferredSizeOverride: String? = null // Allows specific components (like FullPlayer) to still request high-res
): String? {
    if (originalUrl.isNullOrBlank()) {
        return null
    }

    if (!originalUrl.contains("mzstatic.com")) {
        return originalUrl // Return original if not an mzstatic URL
    }

    val targetSize = preferredSizeOverride ?: when (imageQualityKey) {
        AppPreferenceConstants.IMAGE_QUALITY_LOW -> LOW_RES_SIZE
        AppPreferenceConstants.IMAGE_QUALITY_MEDIUM -> MEDIUM_RES_SIZE
        AppPreferenceConstants.IMAGE_QUALITY_AUTO -> DEFAULT_HIGH_RES_SIZE // "Auto" here means default high
        else -> DEFAULT_HIGH_RES_SIZE // Fallback
    }

    return ITUNES_ARTWORK_SIZE_REGEX.replace(originalUrl) { matchResult ->
        val bbSuffix = matchResult.groupValues.getOrNull(2) ?: ""
        Timber.d("getHighResArtworkUrl: URL: '$originalUrl', Quality: '$imageQualityKey', TargetSize: '$targetSize'")
        "/${targetSize}${bbSuffix}.jpg"
    }.ifEmpty { originalUrl }
}
/**
 * Generates a prioritized, context-aware list of artwork URLs for a given PlaybackItem.
 * Coil will attempt to load from this list in order, using the first one that succeeds.
 *
 * @param item The PlaybackItem to get artwork for.
 * @param quality The desired quality for the *fallback* YouTube thumbnail. This is key
 * for requesting a high-res image for the player and a medium-res one for list items.
 * @return A list of URL strings in order of priority.
 */
fun generateArtworkUrlList(item: PlaybackItem?, quality: ThumbnailQuality): List<String> {
    if (item == null) return emptyList()

    val urls = mutableListOf<String>()

    // PRIORITY 1: The song's specific artwork URI from mzstatic.
    // We will use getHighResArtworkUrl to ensure we get a decently sized version.
    if (!item.artworkUri.isNullOrBlank() && item.artworkUri.contains("mzstatic.com")) {
        // For song-specific art, we usually want a high quality version regardless of context.
        // We can use the existing utility for this.
        val highResSongArt = getHighResArtworkUrl(item.artworkUri, AppPreferenceConstants.IMAGE_QUALITY_AUTO)
        if(highResSongArt != null) {
            urls.add(highResSongArt)
        }
    }

    // PRIORITY 2 (FALLBACK): YouTube thumbnails for the parent video, at the requested quality.
    urls.addAll(getYouTubeThumbnailUrl(item.videoId, quality))

    // As a final fallback, you could add the channel photo, but for now, we'll stick to
    // the user's request: song art -> video thumbnail.
    // if (!item.artworkUri.isNullOrBlank()) { urls.add(item.artworkUri) } // Fallback to channel photo if it was passed

    return urls.distinct()
}