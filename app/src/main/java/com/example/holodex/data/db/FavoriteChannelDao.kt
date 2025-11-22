// File: java/com/example/holodex/data/db/FavoriteChannelDao.kt

package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteChannelDao {

    @Query("SELECT * FROM favorite_channels WHERE is_deleted = 0 ORDER BY favorited_at_timestamp DESC")
    fun getFavoriteChannels(): Flow<List<FavoriteChannelEntity>>

    @Query("SELECT id FROM favorite_channels WHERE is_deleted = 0")
    fun getFavoriteChannelIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: FavoriteChannelEntity)

    /**
     * Marks a channel for deletion. It will be removed from the UI
     * and queued for deletion on the server during the next sync.
     * --- FIX: Update the query to use the PENDING_DELETE enum value ---
     */
    @Query("UPDATE favorite_channels SET is_deleted = 1, sync_status = 'PENDING_DELETE' WHERE id = :channelId")
    suspend fun softDelete(channelId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_channels WHERE id = :channelId AND is_deleted = 0 LIMIT 1)")
    fun isFavorited(channelId: String): Flow<Boolean>

    // --- NEW METHODS FOR SYNC ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channels: List<FavoriteChannelEntity>)

    // --- FIX: Update the query to use the DIRTY enum value ---
    @Query("SELECT * FROM favorite_channels WHERE sync_status = 'DIRTY'")
    suspend fun getDirtyItems(): List<FavoriteChannelEntity>

    // --- FIX: Update the query to use the PENDING_DELETE enum value ---
    @Query("SELECT id FROM favorite_channels WHERE sync_status = 'PENDING_DELETE'")
    suspend fun getPendingDeletionIds(): List<String>

    // --- FIX: Update the query to use the SYNCED enum value ---
    @Query("UPDATE favorite_channels SET sync_status = 'SYNCED' WHERE id IN (:channelIds)")
    suspend fun markAsSynced(channelIds: List<String>)

    @Query("DELETE FROM favorite_channels WHERE id IN (:channelIds) AND sync_status = 'PENDING_DELETE'")
    suspend fun deleteSyncedDeletions(channelIds: List<String>)
}