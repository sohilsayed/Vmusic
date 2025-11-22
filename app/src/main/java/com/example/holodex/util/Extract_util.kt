// File: java/com/example/holodex/util/Extract_util.kt
package com.example.holodex.util

import timber.log.Timber

// Made into a top-level function as it was called that way
// Alternatively, make Extract_util an object and call Extract_util.extractVideoIdFromQuery
fun extractVideoIdFromQuery(query: String): String? {
    val trimmedQuery = query.trim()
    val videoIdRegex = Regex("^[a-zA-Z0-9_-]{11}$")
    if (videoIdRegex.matches(trimmedQuery)) {
        Timber.Forest.d("extractVideoIdFromQuery: Matched direct video ID: $trimmedQuery")
        return trimmedQuery
    }

    // Regex for Holodex Music URL (music.holodex.net/video/ID or holodex.net/watch/ID)
    // Allows for optional trailing slashes or query params after ID
    val holodexUrlRegex = Regex("""^https://(music\.)?holodex\.net/(video|watch)/([a-zA-Z0-9_-]{11})""")
    var matcher = holodexUrlRegex.find(trimmedQuery)
    if (matcher != null) {
        val videoId = matcher.groupValues[3] // Group 3 is the ID
        Timber.Forest.d("extractVideoIdFromQuery: Matched Holodex URL, extracted ID: $videoId")
        return videoId
    }

    // Regex for YouTube URLs (youtube.com/watch?v=ID or youtu.be/ID)
    // More comprehensive regex to catch various YouTube URL formats
    val youtubeUrlRegex = Regex("""(?:youtube\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?)/|.*[?&]v=)|youtu\.be/|youtube\.com/live/)([a-zA-Z0-9_-]{11})""")
    matcher = youtubeUrlRegex.find(trimmedQuery)
    if (matcher != null) {
        val videoId = matcher.groupValues[1] // Group 1 is the ID
        Timber.Forest.d("extractVideoIdFromQuery: Matched YouTube URL, extracted ID: $videoId")
        return videoId
    }

    Timber.Forest.d("extractVideoIdFromQuery: No video ID extracted from query: $trimmedQuery")
    return null
}
