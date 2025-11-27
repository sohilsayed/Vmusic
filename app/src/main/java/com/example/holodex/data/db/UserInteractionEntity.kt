package com.example.holodex.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "user_interactions",
    primaryKeys = ["itemId", "interactionType"],
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
data class UserInteractionEntity(
    val itemId: String,
    val interactionType: String,
    val timestamp: Long,

    val localFilePath: String? = null,
    val downloadStatus: String? = null,
    val downloadFileName: String? = null,
    val downloadTrackNum: Int? = null,
    val downloadTargetFormat: String? = null,

    @ColumnInfo(defaultValue = "0")
    val downloadProgress: Int = 0,

    val serverId: String? = null,

    @ColumnInfo(defaultValue = "'SYNCED'")
    val syncStatus: String = "SYNCED"
)