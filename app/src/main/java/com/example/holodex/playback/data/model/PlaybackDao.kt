package com.example.holodex.playback.data.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.holodex.data.db.UnifiedItemProjection

@Dao
interface PlaybackDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPlaybackState(state: PlaybackStateEntity)

    @Query("DELETE FROM playback_queue_ref")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueRefs(refs: List<PlaybackQueueRefEntity>)

    @Transaction
    suspend fun saveQueueAndState(
        state: PlaybackStateEntity,
        activeQueue: List<PlaybackQueueRefEntity>,
        backupQueue: List<PlaybackQueueRefEntity>
    ) {
        clearQueue()
        setPlaybackState(state)
        if (activeQueue.isNotEmpty()) {
            insertQueueRefs(activeQueue)
        }
        if (backupQueue.isNotEmpty()) {
            insertQueueRefs(backupQueue)
        }
    }

    // --- READS ---
    @Query("SELECT * FROM playback_state WHERE id = 0 LIMIT 1")
    suspend fun getState(): PlaybackStateEntity?

    // FIX: Join with Unified Metadata to get the full objects back
    @Transaction
    @Query("""
        SELECT M.* FROM unified_metadata M
        INNER JOIN playback_queue_ref Q ON M.id = Q.item_id
        WHERE Q.is_backup = 0
        ORDER BY Q.queue_index ASC
    """)
    suspend fun getActiveQueueWithMetadata(): List<UnifiedItemProjection>

    @Transaction
    @Query("""
        SELECT M.* FROM unified_metadata M
        INNER JOIN playback_queue_ref Q ON M.id = Q.item_id
        WHERE Q.is_backup = 1
        ORDER BY Q.queue_index ASC
    """)
    suspend fun getBackupQueueWithMetadata(): List<UnifiedItemProjection>
}