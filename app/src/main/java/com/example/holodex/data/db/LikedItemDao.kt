// File: java/com/example/holodex/data/db/LikedItemDao.kt

package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(likedItem: LikedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(likedItems: List<LikedItemEntity>)

    @Query("SELECT * FROM liked_items WHERE sync_status != 'SYNCED'")
    suspend fun getUnsyncedItems(): List<LikedItemEntity>

    @Query("SELECT * FROM liked_items WHERE sync_status = 'DIRTY' AND server_id IS NULL")
    suspend fun getOrphanedDirtyItems(): List<LikedItemEntity>

    @Query("DELETE FROM liked_items WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_items WHERE itemId = :itemId AND sync_status != 'PENDING_DELETE' LIMIT 1)")
    fun isLiked(itemId: String): Flow<Boolean>

    @Query("SELECT * FROM liked_items WHERE itemId = :itemId LIMIT 1")
    suspend fun getLikedItem(itemId: String): LikedItemEntity?

    @Query("SELECT * FROM liked_items WHERE sync_status != 'PENDING_DELETE' ORDER BY liked_at DESC")
    fun getAllLikedItemsSortedByDate(): Flow<List<LikedItemEntity>>

    @Query("SELECT * FROM liked_items")
    suspend fun getAllLikedItemsOnce(): List<LikedItemEntity>

    @Query("DELETE FROM liked_items WHERE sync_status = 'SYNCED' AND item_type = 'SONG_SEGMENT'")
    suspend fun deleteAllSyncedSongSegments()

    @Query("UPDATE liked_items SET sync_status = :status, last_modified_at = :timestamp WHERE itemId = :itemId")
    suspend fun updateStatusAndTimestamp(itemId: String, status: SyncStatus, timestamp: Long)

    @Query("SELECT * FROM liked_items WHERE item_type = 'VIDEO' AND sync_status != 'PENDING_DELETE' ORDER BY liked_at DESC")
    fun getFavoritedVideosSortedByDate(): Flow<List<LikedItemEntity>>

    @Query("SELECT * FROM liked_items WHERE item_type = 'SONG_SEGMENT' AND sync_status != 'PENDING_DELETE' ORDER BY liked_at DESC")
    fun getLikedSongSegmentsSortedByDate(): Flow<List<LikedItemEntity>>

    @Query("SELECT DISTINCT videoId FROM liked_items WHERE sync_status != 'PENDING_DELETE'")
    fun getAllDistinctLikedVideoIds(): Flow<List<String>>

    @Query("DELETE FROM liked_items")
    suspend fun clearAll()

    @Query("UPDATE liked_items SET sync_status = 'PENDING_DELETE', last_modified_at = :timestamp WHERE itemId = :itemId")
    suspend fun markForDeletion(itemId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM liked_items WHERE item_type = :itemTypeName AND sync_status != 'PENDING_DELETE' ORDER BY liked_at DESC")
    fun getLikedItemsByTypeSortedByDate(itemTypeName: String): Flow<List<LikedItemEntity>>

    @Query("SELECT * FROM liked_items WHERE item_type = :itemTypeName AND sync_status != 'PENDING_DELETE' ORDER BY liked_at DESC LIMIT :limit OFFSET :offset")
    fun getLikedItemsByTypePaged(itemTypeName: String, limit: Int, offset: Int): Flow<List<LikedItemEntity>>

    @Query("SELECT COUNT(itemId) FROM liked_items WHERE item_type = :itemTypeName AND sync_status != 'PENDING_DELETE'")
    suspend fun countLikedItemsByType(itemTypeName: String): Int

    @Query("UPDATE liked_items SET sync_status = 'PENDING_DELETE', last_modified_at = :timestamp WHERE itemId = :itemId")
    suspend fun performMarkForDeletion(itemId: String, timestamp: Long)

    @Query("SELECT * FROM liked_items WHERE sync_status = 'DIRTY'")
    suspend fun getDirtyItems(): List<LikedItemEntity>

    @Query("SELECT itemId FROM liked_items WHERE sync_status = 'PENDING_DELETE'")
    suspend fun getPendingDeletionIds(): List<String>

    @Query("UPDATE liked_items SET sync_status = 'SYNCED' WHERE itemId IN (:itemIds)")
    suspend fun markAsSynced(itemIds: List<String>)

    @Query("DELETE FROM liked_items WHERE itemId IN (:itemIds) AND sync_status = 'PENDING_DELETE'")
    suspend fun deleteSyncedDeletions(itemIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<LikedItemEntity>)

    @Query("SELECT itemId FROM liked_items WHERE itemId IN (:itemIds)")
    suspend fun getLikedItemIds(itemIds: List<String>): List<String>
}