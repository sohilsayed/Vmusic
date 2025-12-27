package com.example.holodex.data.db

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A Room Projection that combines Metadata with User Interactions.
 *
 * Note: The mapping logic has been moved to UnifiedDisplayItemMapper.kt
 * to separate concerns and allow for cleaner ID parsing.
 * This class should hold data ONLY.
 */
data class UnifiedItemProjection(
    @Embedded val metadata: UnifiedMetadataEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val interactions: List<UserInteractionEntity>
)