// File: java/com/example/holodex/util/ImageUtils.kt
package com.example.holodex.util // Or your preferred util package

import com.example.holodex.data.AppPreferenceConstants
import com.example.holodex.playback.domain.model.PlaybackItem
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
 * INTELLIGENT ARTWORK GENERATOR
 * Ensures High Quality is used when requested, overriding lower quality DB defaults.
 */
fun generateArtworkUrlList(item: PlaybackItem?, quality: ThumbnailQuality): List<String> {
    if (item == null) return emptyList()

    val urls = mutableListOf<String>()

    // Check if the item provided a specific URL (from DB)
    val providedUrl = item.artworkUri

    // Logic: Is the provided URL a "Low Quality" YouTube thumb?
    // mqdefault = Medium, default = Low.
    val isLowResYouTube = providedUrl != null && (providedUrl.contains("mqdefault.jpg") || providedUrl.contains("default.jpg"))

    // 1. If we want MAX quality AND the provided URL is low-res YouTube, SKIP IT.
    // We want to force the generator to pick the HD version.
    if (quality == ThumbnailQuality.MAX && isLowResYouTube) {
        // Do nothing, skip adding providedUrl
    }
    // 2. Otherwise (it's iTunes, Holodex, or we want low res anyway), add it.
    else if (!providedUrl.isNullOrBlank()) {
        // If it's iTunes, upgrade it if needed
        val processedUrl = if (providedUrl.contains("mzstatic.com")) {
            getHighResArtworkUrl(providedUrl, AppPreferenceConstants.IMAGE_QUALITY_AUTO)
        } else {
            providedUrl
        }
        if (processedUrl != null) urls.add(processedUrl)
    }

    // 3. Append the YouTube candidates for the requested quality
    // If quality is MAX, this adds maxresdefault.jpg at the correct position
    urls.addAll(getYouTubeThumbnailUrl(item.videoId, quality))

    return urls.distinct()
}
/**
 * Instantly guesses the aspect ratio based on the URL domain.
 * This saves us from waiting for the image to load to determine layout.
 */
fun guessAspectRatioFromUrl(url: String?): Float {
    if (url.isNullOrBlank()) return 16f / 9f // Default to video style

    return when {
        // iTunes/Apple Music artwork is ALWAYS square
        url.contains("mzstatic.com") -> 1f
        // YouTube thumbnails are standard 16:9
        url.contains("ytimg.com") -> 16f / 9f
        url.contains("youtube.com") -> 16f / 9f
        // Holodex channel avatars are usually square
        url.contains("channelImg") -> 1f
        // Default fallback
        else -> 16f / 9f
    }
}