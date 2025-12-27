package com.example.holodex.util

/**
 * Single source of truth for parsing Holodex/YouTube IDs.
 *
 * Logic:
 * - YouTube Video IDs are strictly 11 characters.
 * - Our Composite IDs are "VIDEOID_STARTTIME".
 * - Therefore, a separator "_" is valid ONLY if it appears after the 11th character.
 */
object IdUtil {

    // Regex Explanation:
    // ^(.{11})  -> Capture exactly 11 characters at the start (The Video ID)
    // _         -> The separator
    // (\d+)$    -> Capture digits at the end (The Start Time)
    private val SEGMENT_PATTERN = Regex("^(.{11})_(\\d+)$")

    /**
     * Extracts the raw YouTube Video ID.
     * Handles cases like "o_123-abcde" (Valid ID with underscore) correctly
     * by adhering to the 11-character rule.
     */
    fun extractVideoId(rawId: String): String {
        // If it's not long enough to be a composite, return as is.
        if (rawId.length <= 11) return rawId

        val match = SEGMENT_PATTERN.matchEntire(rawId)
        return if (match != null) {
            match.groupValues[1] // Return the first 11 chars
        } else {
            rawId // It's a channel ID or just a weird ID, return as is
        }
    }

    /**
     * Extracts the start time. Returns 0 if not a segment.
     */
    fun extractStartSeconds(rawId: String): Int {
        val match = SEGMENT_PATTERN.matchEntire(rawId)
        return match?.groupValues?.get(2)?.toIntOrNull() ?: 0
    }

    /**
     * Creates a composite ID safely.
     */
    fun createCompositeId(videoId: String, startSeconds: Int): String {
        return "${videoId}_$startSeconds"
    }
}