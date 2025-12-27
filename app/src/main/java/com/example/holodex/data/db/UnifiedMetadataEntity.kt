package com.example.holodex.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.holodex.util.IdUtil
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.getYouTubeThumbnailUrl

@Entity(tableName = "unified_metadata")
data class UnifiedMetadataEntity(
    @PrimaryKey val id: String,

    val title: String,
    val artistName: String,
    val type: String,

    val specificArtUrl: String?,
    val uploaderAvatarUrl: String?,

    val duration: Long,

    @ColumnInfo(defaultValue = "NULL") val startSeconds: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val endSeconds: Long? = null,
    val parentVideoId: String? = null,

    val channelId: String,
    val org: String? = null,
    val topicId: String? = null,

    @ColumnInfo(defaultValue = "'past'")
    val status: String = "past",

    val availableAt: String? = null,
    val publishedAt: String? = null,

    @ColumnInfo(defaultValue = "0")
    val songCount: Int = 0,

    val description: String? = null,

    @ColumnInfo(defaultValue = "0")
    val lastUpdatedAt: Long = System.currentTimeMillis()
) {

    fun getComputedArtworkList(): List<String> {
        val urls = mutableListOf<String>()

        // CASE 1: CHANNEL
        if (type == "CHANNEL") {
            if (!specificArtUrl.isNullOrBlank()) urls.add(specificArtUrl)
            if (!uploaderAvatarUrl.isNullOrBlank()) urls.add(uploaderAvatarUrl)
            return urls
        }

        // CASE 2: SEGMENT (Song) - Special Handling
        // Only prioritize specificArtUrl if it's high-quality (mzstatic)
        if (type == "SEGMENT" && !specificArtUrl.isNullOrBlank() && specificArtUrl.contains("mzstatic.com")) {
            urls.add(specificArtUrl)
        }

        // CASE 3: VIDEO IMAGE
        val targetId = parentVideoId ?: id
        val cleanId = IdUtil.extractVideoId(targetId)

        // FIX: Use MEDIUM instead of MAX.
        // Lists display images at ~60-100dp. MAX (1280x720) is wasteful.
        // MEDIUM (320x180) is perfect and loads much faster.
        urls.addAll(getYouTubeThumbnailUrl(cleanId, ThumbnailQuality.MEDIUM))

        // CASE 4: FALLBACKS
        if (!specificArtUrl.isNullOrBlank() && !urls.contains(specificArtUrl)) {
            urls.add(specificArtUrl)
        }

        if (!uploaderAvatarUrl.isNullOrBlank()) {
            urls.add(uploaderAvatarUrl)
        }

        return urls.distinct()
    }
}