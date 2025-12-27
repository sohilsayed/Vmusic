package com.example.holodex.data.mappers

import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.util.IdUtil

fun HolodexVideoItem.toUnifiedMetadataEntity(
    overrideType: String? = null
): UnifiedMetadataEntity {
    // Logic: Songs are segments, the parent is a VIDEO.
    val type = overrideType ?: "VIDEO"

    // Use channel photo as fallback art
    val art = this.channel.photoUrl

    return UnifiedMetadataEntity(
        id = this.id,
        title = this.title,
        artistName = this.channel.name,
        type = type,
        specificArtUrl = art,
        uploaderAvatarUrl = this.channel.photoUrl,
        duration = this.duration,
        channelId = this.channel.id ?: "unknown",
        org = this.channel.org,
        topicId = this.topicId,
        status = this.status,
        availableAt = this.availableAt,
        publishedAt = this.publishedAt,
        songCount = this.songcount ?: this.songs?.size ?: 0,
        description = this.description,
        lastUpdatedAt = System.currentTimeMillis()
    )
}

// Helper to flatten a Video + Its Songs into a list of Metadata Entities
fun HolodexVideoItem.toFlattenedMetadataEntities(): List<UnifiedMetadataEntity> {
    val list = mutableListOf<UnifiedMetadataEntity>()

    // 1. Add the Video itself
    list.add(this.toUnifiedMetadataEntity())

    // 2. Add Segments (Songs)
    this.songs?.forEach { song ->
        val segmentId = IdUtil.createCompositeId(this.id, song.start)
        list.add(UnifiedMetadataEntity(
            id = segmentId,
            title = song.name,
            artistName = song.originalArtist ?: this.channel.name,
            type = "SEGMENT",
            specificArtUrl = song.artUrl,
            uploaderAvatarUrl = this.channel.photoUrl,
            duration = (song.end - song.start).toLong(),
            channelId = this.channel.id ?: "unknown",
            parentVideoId = this.id, // CRITICAL: This links the segment to the video
            startSeconds = song.start.toLong(),
            endSeconds = song.end.toLong(),
            org = this.channel.org,
            lastUpdatedAt = System.currentTimeMillis()
        ))
    }
    return list
}