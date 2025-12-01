// File: java\com\example\holodex\data\db\mappers\SyncMappers.kt (NEW FILE)
package com.example.holodex.data.db.mappers

import com.example.holodex.data.api.PlaylistDto
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.data.db.SyncStatus


fun PlaylistDto.toEntity(): PlaylistEntity {
    return PlaylistEntity(
        playlistId = 0, // Let Room auto-generate the local ID
        serverId = this.id,
        name = this.title,
        description = this.description,
        owner = this.owner,
        type = this.type ?: "ugp",
        createdAt = this.createdAt,
        last_modified_at = this.updatedAt,
        isDeleted = false,
        syncStatus = SyncStatus.SYNCED // Data from server is considered synced
    )
}