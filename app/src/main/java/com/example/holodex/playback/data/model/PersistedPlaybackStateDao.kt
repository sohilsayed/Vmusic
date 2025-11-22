// File: java/com/example/holodex/playback/data/model/PersistedPlaybackStateDao.kt
package com.example.holodex.playback.data.model

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction

data class PlaybackStateWithItemsTuple(
    @Embedded val state: PersistedPlaybackStateEntity,
    @Relation(
        parentColumn = "queueIdentifier",
        entityColumn = "ownerQueueIdentifier"
    )
    val items: List<PersistedPlaybackItemEntity>
)

@Dao
interface PersistedPlaybackStateDao {

    @Transaction
    @Query("SELECT * FROM persisted_playback_state_table WHERE queueIdentifier = :targetQueueIdentifier LIMIT 1")
    suspend fun getPlaybackStateWithItems(targetQueueIdentifier: String = "default_queue"): PlaybackStateWithItemsTuple?

    @Transaction
    suspend fun savePlaybackStateWithItems(stateEntity: PersistedPlaybackStateEntity, itemEntities: List<PersistedPlaybackItemEntity>) {
        clearPlaybackStateByQueueId(stateEntity.queueIdentifier)
        clearPlaybackItemsByQueueId(stateEntity.queueIdentifier)
        insertPlaybackState(stateEntity)
        insertPlaybackItems(itemEntities)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackState(state: PersistedPlaybackStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackItems(items: List<PersistedPlaybackItemEntity>)

    @Query("DELETE FROM persisted_playback_state_table WHERE queueIdentifier = :targetQueueIdentifier")
    suspend fun clearPlaybackStateByQueueId(targetQueueIdentifier: String)

    @Query("DELETE FROM persisted_playback_items_table WHERE ownerQueueIdentifier = :targetQueueIdentifier")
    suspend fun clearPlaybackItemsByQueueId(targetQueueIdentifier: String)
}