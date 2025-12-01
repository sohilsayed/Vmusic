package com.example.holodex.playback.data.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.holodex.data.db.UnifiedItemProjection

@Dao
interface PlaybackDao {

    // --- WRITES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPlaybackState(state: PlaybackStateEntity)

    @Query("DELETE FROM playback_queue_ref")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueRefs(refs: List<PlaybackQueueRefEntity>)

    @Transaction
    suspend fun saveFullState(
        state: PlaybackStateEntity,
        activeQueue: List<PlaybackQueueRefEntity>,
        backupQueue: List<PlaybackQueueRefEntity>
    ) {
        // Atomic transaction: Clear old state -> Save new state
        clearQueue()
        setPlaybackState(state)
        insertQueueRefs(activeQueue)
        if (backupQueue.isNotEmpty()) {
            insertQueueRefs(backupQueue)
        }
    }

    // --- READS ---

    @Query("SELECT * FROM playback_state WHERE id = 0 LIMIT 1")
    suspend fun getState(): PlaybackStateEntity?

    /**
     * Fetches the Active Queue (Shuffled or Normal)
     * Joins with UnifiedMetadata so we get Titles/Images instantly.
     */
    @Transaction
    @Query("""
        SELECT M.* FROM unified_metadata M
        INNER JOIN playback_queue_ref Q ON M.id = Q.item_id
        WHERE Q.is_backup = 0
        ORDER BY Q.queue_index ASC
    """)
    suspend fun getActiveQueueWithMetadata(): List<UnifiedItemProjection>

    /**
     * Fetches the Backup Queue (Original Order)
     * Used to restore order when un-shuffling.
     */
    @Transaction
    @Query("""
        SELECT M.* FROM unified_metadata M
        INNER JOIN playback_queue_ref Q ON M.id = Q.item_id
        WHERE Q.is_backup = 1
        ORDER BY Q.queue_index ASC
    """)
    suspend fun getBackupQueueWithMetadata(): List<UnifiedItemProjection>
}