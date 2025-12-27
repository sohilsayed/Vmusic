package com.example.holodex.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A log-style history table.
 * - Local Only (No sync).
 * - Ever-growing (Duplicates allowed, e.g., played the same song twice).
 * - Links to UnifiedMetadata for details (Title, Art).
 */
@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = UnifiedMetadataEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId")]
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0, // Auto-incrementing log ID

    val itemId: String, // The song/video ID
    val timestamp: Long = System.currentTimeMillis()
)