package com.example.holodex.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
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

    // *** FIX: Explicit default value to match SQL ***
    @ColumnInfo(defaultValue = "'past'")
    val status: String = "past",

    val availableAt: String? = null,
    val publishedAt: String? = null,

    // *** FIX: Explicit default value to match SQL ***
    @ColumnInfo(defaultValue = "0")
    val songCount: Int = 0,

    val description: String? = null,

    @ColumnInfo(defaultValue = "0")
    val lastUpdatedAt: Long = System.currentTimeMillis()
) {
    fun getComputedArtworkList(): List<String> {
        val urls = mutableListOf<String>()
        if (!specificArtUrl.isNullOrBlank()) urls.add(specificArtUrl)

        val targetId = parentVideoId ?: if (type != "CHANNEL") id else null
        if (targetId != null) {
            urls.addAll(getYouTubeThumbnailUrl(targetId, ThumbnailQuality.MEDIUM))
        }

        if (!uploaderAvatarUrl.isNullOrBlank()) urls.add(uploaderAvatarUrl)
        return urls
    }
}