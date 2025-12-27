package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UnifiedDao {

    // --- METADATA (The Cache) ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMetadataIgnore(metadata: UnifiedMetadataEntity): Long

    @Update
    suspend fun updateMetadataRaw(metadata: UnifiedMetadataEntity)

    @Transaction
    suspend fun upsertMetadata(metadata: UnifiedMetadataEntity) {
        val rowId = insertMetadataIgnore(metadata)
        if (rowId == -1L) {
            updateMetadataRaw(metadata)
        }
    }

    @Transaction
    suspend fun upsertMetadataList(list: List<UnifiedMetadataEntity>) {
        list.forEach { upsertMetadata(it) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMetadataBatch(list: List<UnifiedMetadataEntity>)

    @Transaction
    @Query("SELECT * FROM unified_metadata WHERE parentVideoId = :videoId ORDER BY startSeconds ASC")
    fun getSegmentsForVideo(videoId: String): Flow<List<UnifiedItemProjection>>

    // --- OPTIMIZED READERS (Performance Fix) ---

    // 1. Bulk Hydration for Feeds (Search/Browse)
    @Query("""
        SELECT 
            M.*,
            EXISTS(SELECT 1 FROM user_interactions WHERE itemId = M.id AND interactionType = 'LIKE' AND syncStatus != 'PENDING_DELETE') as isLiked,
            EXISTS(SELECT 1 FROM user_interactions WHERE itemId = M.id AND interactionType = 'DOWNLOAD' AND downloadStatus = 'COMPLETED') as isDownloaded,
            (SELECT downloadStatus FROM user_interactions WHERE itemId = M.id AND interactionType = 'DOWNLOAD') as downloadStatus,
            (SELECT localFilePath FROM user_interactions WHERE itemId = M.id AND interactionType = 'DOWNLOAD') as localFilePath,
            NULL as historyId  -- FIX: Provide null for non-history items
        FROM unified_metadata M
        WHERE M.id IN (:ids)
    """)
    suspend fun getOptimizedItemsByIds(ids: List<String>): List<UnifiedItemWithStatus>

    // 2. History Feed
    @Query("""
        SELECT 
            M.*,
            EXISTS(SELECT 1 FROM user_interactions WHERE itemId = M.id AND interactionType = 'LIKE' AND syncStatus != 'PENDING_DELETE') as isLiked,
            EXISTS(SELECT 1 FROM user_interactions WHERE itemId = M.id AND interactionType = 'DOWNLOAD' AND downloadStatus = 'COMPLETED') as isDownloaded,
            (SELECT downloadStatus FROM user_interactions WHERE itemId = M.id AND interactionType = 'DOWNLOAD') as downloadStatus,
            (SELECT localFilePath FROM user_interactions WHERE itemId = M.id AND interactionType = 'DOWNLOAD') as localFilePath,
            H.id as historyId -- FIX: Select the unique History Log ID
        FROM unified_metadata M
        INNER JOIN playback_history H ON M.id = H.itemId
        ORDER BY H.timestamp DESC
    """)
    fun getOptimizedHistoryFeed(): Flow<List<UnifiedItemWithStatus>>

    // 3. Favorites Feed
    @Query("""
        SELECT 
            M.*,
            1 as isLiked,
            EXISTS(SELECT 1 FROM user_interactions WHERE itemId = M.id AND interactionType = 'DOWNLOAD' AND downloadStatus = 'COMPLETED') as isDownloaded,
            (SELECT downloadStatus FROM user_interactions WHERE itemId = M.id AND interactionType = 'DOWNLOAD') as downloadStatus,
            (SELECT localFilePath FROM user_interactions WHERE itemId = M.id AND interactionType = 'DOWNLOAD') as localFilePath,
            NULL as historyId -- FIX
        FROM unified_metadata M
        INNER JOIN user_interactions I ON M.id = I.itemId
        WHERE I.interactionType = 'LIKE' 
        AND I.syncStatus != 'PENDING_DELETE'
        ORDER BY I.timestamp DESC
    """)
    fun getOptimizedFavoritesFeed(): Flow<List<UnifiedItemWithStatus>>



    @Transaction
    @Query("""
        SELECT M.* FROM unified_metadata M
        INNER JOIN user_interactions I ON M.id = I.itemId
        WHERE I.interactionType = 'FAV_CHANNEL' AND I.syncStatus != 'PENDING_DELETE'
    """)
    suspend fun getFavoriteChannelsSync(): List<UnifiedItemProjection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInteraction(interaction: UserInteractionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryLog(log: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    @Transaction
    @Query("SELECT * FROM unified_metadata WHERE id = :id")
    fun getItemByIdFlow(id: String): Flow<UnifiedItemProjection?>

    // Kept for backward compatibility if needed, but getOptimizedItemsByIds is preferred
    @Transaction
    @Query("SELECT * FROM unified_metadata WHERE id IN (:ids)")
    suspend fun getProjectionsByIds(ids: List<String>): List<UnifiedItemProjection>

    // Kept for Repository access
    @Transaction
    @Query("""
        SELECT M.* FROM unified_metadata M
        INNER JOIN user_interactions I ON M.id = I.itemId
        WHERE I.interactionType = 'LIKE' 
        AND I.syncStatus != 'PENDING_DELETE'
        ORDER BY I.timestamp DESC
    """)
    fun getFavoritesFeed(): Flow<List<UnifiedItemProjection>>

    @Transaction
    @Query("""
        SELECT M.* FROM unified_metadata M
        INNER JOIN user_interactions I ON M.id = I.itemId
        WHERE I.interactionType = 'DOWNLOAD' 
        AND (I.downloadStatus = 'COMPLETED' OR I.downloadStatus = 'FAILED' OR I.downloadStatus = 'EXPORT_FAILED')
        ORDER BY I.timestamp DESC
    """)
    fun getDownloadsFeed(): Flow<List<UnifiedItemProjection>>

    // FIX: Optimized version for the Library screen
    @Query("""
        SELECT 
            M.*,
            EXISTS(SELECT 1 FROM user_interactions WHERE itemId = M.id AND interactionType = 'LIKE' AND syncStatus != 'PENDING_DELETE') as isLiked,
            1 as isDownloaded, -- We consider it "in the downloads list" even if failed, status handles the UI
            I.downloadStatus,
            I.localFilePath,
            NULL as historyId
        FROM unified_metadata M
        INNER JOIN user_interactions I ON M.id = I.itemId
        WHERE I.interactionType = 'DOWNLOAD' 
        AND (I.downloadStatus = 'COMPLETED' OR I.downloadStatus = 'FAILED' OR I.downloadStatus = 'EXPORT_FAILED')
        ORDER BY I.timestamp DESC
    """)
    fun getOptimizedDownloadsFeed(): Flow<List<UnifiedItemWithStatus>>

    @Query("SELECT itemId FROM user_interactions WHERE interactionType = 'LIKE' AND syncStatus != 'PENDING_DELETE'")
    fun getLikedItemIds(): Flow<List<String>>

    @Query("""
        DELETE FROM unified_metadata 
        WHERE lastUpdatedAt < :threshold 
        AND id NOT IN (SELECT itemId FROM user_interactions)
        AND id NOT IN (SELECT video_id_for_item FROM playlist_items) 
        AND id NOT IN (SELECT itemId FROM playback_history)
        AND id NOT IN (SELECT item_id FROM playback_queue_ref) 
    """)
    suspend fun pruneOrphanedMetadata(threshold: Long)

    @Query("SELECT * FROM user_interactions WHERE interactionType = :type AND syncStatus = 'DIRTY'")
    suspend fun getDirtyInteractions(type: String): List<UserInteractionEntity>

    @Query("SELECT * FROM user_interactions WHERE interactionType = :type AND syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteInteractions(type: String): List<UserInteractionEntity>

    @Query("SELECT * FROM user_interactions WHERE interactionType = :type AND syncStatus = 'SYNCED'")
    suspend fun getSyncedItems(type: String): List<UserInteractionEntity>

    @Query("UPDATE user_interactions SET serverId = :serverId, syncStatus = 'SYNCED' WHERE itemId = :itemId AND interactionType = :type")
    suspend fun confirmUpload(itemId: String, type: String, serverId: String)

    @Query("DELETE FROM user_interactions WHERE itemId = :itemId AND interactionType = :type")
    suspend fun confirmDeletion(itemId: String, type: String)

    @Query("UPDATE user_interactions SET syncStatus = 'SYNCED' WHERE itemId IN (:ids) AND interactionType = :type")
    suspend fun markBatchAsSynced(ids: List<String>, type: String)

    @Query("DELETE FROM user_interactions WHERE itemId IN (:ids) AND interactionType = :type AND syncStatus = 'PENDING_DELETE'")
    suspend fun deleteBatchPending(ids: List<String>, type: String)

    @Query("SELECT * FROM user_interactions WHERE itemId = :itemId AND interactionType = 'DOWNLOAD'")
    suspend fun getDownloadInteraction(itemId: String): UserInteractionEntity?

    @Query("SELECT * FROM user_interactions WHERE itemId = :itemId AND interactionType = 'DOWNLOAD'")
    fun getDownloadInteractionSync(itemId: String): UserInteractionEntity?

    @Query("UPDATE user_interactions SET downloadStatus = :status WHERE itemId = :itemId AND interactionType = 'DOWNLOAD'")
    suspend fun updateDownloadStatus(itemId: String, status: String)

    @Query("UPDATE user_interactions SET localFilePath = :path, downloadStatus = 'COMPLETED', downloadProgress = 100 WHERE itemId = :itemId AND interactionType = 'DOWNLOAD'")
    suspend fun completeDownload(itemId: String, path: String)

    @Query("SELECT * FROM user_interactions WHERE interactionType = 'DOWNLOAD'")
    suspend fun getAllDownloadsOneShot(): List<UserInteractionEntity>

    @Query("UPDATE user_interactions SET serverId = :serverId, syncStatus = 'SYNCED' WHERE itemId = :itemId AND interactionType = :type")
    suspend fun updateServerId(itemId: String, type: String, serverId: String)

    @Query("DELETE FROM user_interactions WHERE itemId = :itemId AND interactionType = :type AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedItem(itemId: String, type: String)

    @Query("SELECT serverId FROM user_interactions WHERE itemId = :itemId AND interactionType = 'LIKE'")
    suspend fun getLikeServerId(itemId: String): String?

    @Query("UPDATE user_interactions SET syncStatus = 'PENDING_DELETE' WHERE itemId = :itemId AND interactionType = :type")
    suspend fun softDeleteInteraction(itemId: String, type: String)

    @Query("DELETE FROM user_interactions WHERE itemId = :itemId AND interactionType = :type")
    suspend fun deleteInteraction(itemId: String, type: String)

    @Query("SELECT COUNT(*) FROM user_interactions WHERE itemId = :itemId AND interactionType = 'LIKE' AND syncStatus != 'PENDING_DELETE'")
    suspend fun isLiked(itemId: String): Int

    @Query("SELECT COUNT(*) FROM user_interactions WHERE itemId = :itemId AND interactionType = 'FAV_CHANNEL' AND syncStatus != 'PENDING_DELETE'")
    suspend fun isChannelLiked(itemId: String): Int

    @Query("SELECT serverId FROM user_interactions WHERE itemId = :itemId AND interactionType = 'FAV_CHANNEL'")
    suspend fun getChannelLikeServerId(itemId: String): String?

    @Query("SELECT * FROM unified_metadata WHERE id = :id")
    suspend fun getItemByIdOneShot(id: String): UnifiedItemProjection?

    @Query("SELECT * FROM user_interactions WHERE interactionType = 'DOWNLOAD' AND downloadStatus = 'COMPLETED' AND itemId IN (:ids)")
    suspend fun getCompletedDownloadsBatch(ids: List<String>): List<UserInteractionEntity>

    @Query("SELECT * FROM unified_metadata WHERE id IN (:ids)")
    suspend fun getMetadatasByIds(ids: List<String>): List<UnifiedMetadataEntity>

    @Query("DELETE FROM user_interactions WHERE interactionType = :type")
    suspend fun deleteAllInteractionsByType(type: String)

    @Transaction
    @Query("""
        SELECT M.* FROM unified_metadata M
        INNER JOIN user_interactions I ON M.id = I.itemId
        WHERE I.interactionType = 'FAV_CHANNEL' AND I.syncStatus != 'PENDING_DELETE'
        ORDER BY I.timestamp DESC
    """)
    fun getFavoriteChannels(): Flow<List<UnifiedItemProjection>>
}