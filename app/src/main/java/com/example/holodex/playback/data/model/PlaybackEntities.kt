package com.example.holodex.playback.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.holodex.data.db.UnifiedMetadataEntity

/**
 * Stores the "Head" of the state: Index, Position, Shuffle Mode.
 * Lightweight snapshot of the player settings.
 */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 0, // Single row architecture
    @ColumnInfo(name = "current_index") val currentIndex: Int,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "shuffle_mode_enabled") val isShuffleEnabled: Boolean,
    @ColumnInfo(name = "repeat_mode") val repeatMode: Int // 0=OFF, 1=ONE, 2=ALL
)

/**
 * Stores the Queue as a list of IDs pointing to the Unified Table.
 * This is the "Database Diet" - no duplicated strings/urls.
 */
@Entity(
    tableName = "playback_queue_ref",
    primaryKeys = ["queue_index", "is_backup"], // unique item at specific position
    foreignKeys = [
        ForeignKey(
            entity = UnifiedMetadataEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("item_id")]
)
data class PlaybackQueueRefEntity(
    @ColumnInfo(name = "queue_index") val sortOrder: Int,
    @ColumnInfo(name = "item_id") val itemId: String,

    // TRUE = The original order (Used when shuffle is OFF)
    // FALSE = The current active order (Used by Player right now)
    @ColumnInfo(name = "is_backup") val isBackup: Boolean
)