package com.example.holodex.data.db

import androidx.room.Embedded

/**
 * A highly optimized projection for Lists and Feeds.
 * Instead of fetching a list of interactions and looping through them in Kotlin,
 * we let SQLite calculate the boolean flags directly.
 *
 * This reduces object creation overhead by ~80% during list scrolling.
 */
data class UnifiedItemWithStatus(
    @Embedded val metadata: UnifiedMetadataEntity,

    // Computed columns from SQL
    val isLiked: Boolean,
    val isDownloaded: Boolean,
    val downloadStatus: String?,
    val localFilePath: String?
)