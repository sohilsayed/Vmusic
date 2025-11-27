package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UnifiedDao {

    // --- WRITES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: UnifiedMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInteraction(interaction: UserInteractionEntity)

    // *** THIS WAS MISSING ***
    @Query("DELETE FROM user_interactions WHERE itemId = :itemId AND interactionType = :type")
    suspend fun deleteInteraction(itemId: String, type: String)

    @Query("UPDATE user_interactions SET syncStatus = 'PENDING_DELETE' WHERE itemId = :itemId AND interactionType = :type")
    suspend fun softDeleteInteraction(itemId: String, type: String)

    @Query("UPDATE user_interactions SET serverId = :serverId, syncStatus = 'SYNCED' WHERE itemId = :itemId AND interactionType = :type")
    suspend fun updateServerId(itemId: String, type: String, serverId: String)

    @Query("UPDATE user_interactions SET syncStatus = 'SYNCED' WHERE itemId IN (:ids) AND interactionType = :type")
    suspend fun markAsSynced(ids: List<String>, type: String)

    @Query("DELETE FROM user_interactions WHERE itemId IN (:ids) AND interactionType = :type AND syncStatus = 'PENDING_DELETE'")
    suspend fun deleteSyncedInteractions(ids: List<String>, type: String)

    // --- CHECKS ---

    @Query("SELECT serverId FROM user_interactions WHERE itemId = :itemId AND interactionType = 'LIKE'")
    suspend fun getLikeServerId(itemId: String): String?

    @Query("SELECT COUNT(*) FROM user_interactions WHERE itemId = :itemId AND interactionType = 'LIKE' AND syncStatus != 'PENDING_DELETE'")
    suspend fun isLiked(itemId: String): Int

    @Query("SELECT COUNT(*) FROM user_interactions WHERE itemId = :itemId AND interactionType = 'FAV_CHANNEL' AND syncStatus != 'PENDING_DELETE'")
    suspend fun isChannelLiked(itemId: String): Int

    @Query("SELECT serverId FROM user_interactions WHERE itemId = :itemId AND interactionType = 'FAV_CHANNEL'")
    suspend fun getChannelLikeServerId(itemId: String): String?

    // --- READS (UI) ---

    @Transaction
    @Query(
        """
        SELECT M.* FROM unified_metadata M
        INNER JOIN user_interactions I ON M.id = I.itemId
        WHERE I.interactionType = 'LIKE' AND I.syncStatus != 'PENDING_DELETE'
        ORDER BY I.timestamp DESC
    """
    )
    fun getFavorites(): Flow<List<UnifiedItemProjection>>

    @Transaction
    @Query(
        """
        SELECT M.* FROM unified_metadata M
        INNER JOIN user_interactions I ON M.id = I.itemId
        WHERE I.interactionType = 'FAV_CHANNEL' AND I.syncStatus != 'PENDING_DELETE'
        ORDER BY I.timestamp DESC
    """
    )
    fun getFavoriteChannels(): Flow<List<UnifiedItemProjection>>

    @Query("SELECT itemId FROM user_interactions WHERE interactionType = 'LIKE' AND syncStatus != 'PENDING_DELETE'")
    fun getLikedItemIds(): Flow<List<String>>

    @Transaction
    @Query(
        """
        SELECT M.* FROM unified_metadata M
        INNER JOIN user_interactions I ON M.id = I.itemId
        WHERE I.interactionType = 'DOWNLOAD'
        ORDER BY I.timestamp DESC
    """
    )
    fun getDownloads(): Flow<List<UnifiedItemProjection>>

    @Transaction
    @Query(
        """
        SELECT M.* FROM unified_metadata M
        INNER JOIN user_interactions I ON M.id = I.itemId
        WHERE I.interactionType = 'HISTORY'
        ORDER BY I.timestamp DESC
    """
    )
    fun getHistory(): Flow<List<UnifiedItemProjection>>

    // --- READS (Sync Worker) ---

    @Query("SELECT * FROM user_interactions WHERE interactionType = :type AND syncStatus = 'DIRTY'")
    suspend fun getDirtyInteractions(type: String): List<UserInteractionEntity>

    @Query("SELECT * FROM user_interactions WHERE interactionType = :type AND syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteInteractions(type: String): List<UserInteractionEntity>

    @Query("SELECT * FROM unified_metadata WHERE id = :id")
    suspend fun getItemByIdOneShot(id: String): UnifiedItemProjection?
    // --- NEW SYNC SPECIFIC QUERIES ---

    // 1. Find items waiting to be uploaded (Additions)
    @Query("SELECT * FROM user_interactions WHERE interactionType = :type AND syncStatus = 'DIRTY'")
    suspend fun getDirtyItems(type: String): List<UserInteractionEntity>

    // 2. Find items waiting to be deleted (Removals)
    @Query("SELECT * FROM user_interactions WHERE interactionType = :type AND syncStatus = 'PENDING_DELETE'")
    suspend fun getPendingDeleteItems(type: String): List<UserInteractionEntity>

    // 3. Find items that are already synced (Reference for Downstream sync)
    @Query("SELECT * FROM user_interactions WHERE interactionType = :type AND syncStatus = 'SYNCED'")
    suspend fun getSyncedItems(type: String): List<UserInteractionEntity>

    // 4. Confirm Upload: Set serverId and mark SYNCED
    @Query("UPDATE user_interactions SET serverId = :serverId, syncStatus = 'SYNCED' WHERE itemId = :itemId AND interactionType = :type")
    suspend fun confirmUpload(itemId: String, type: String, serverId: String)

    // 5. Confirm Deletion: Remove row completely
    @Query("DELETE FROM user_interactions WHERE itemId = :itemId AND interactionType = :type")
    suspend fun confirmDeletion(itemId: String, type: String)

    // 6. Downstream Insert: Insert item from server as SYNCED
    // (Uses upsertInteraction, but logic handles the object creation)

    // 7. Downstream Delete: Remove local SYNCED item because it was gone on server
    @Query("DELETE FROM user_interactions WHERE itemId = :itemId AND interactionType = :type AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedItem(itemId: String, type: String)

    // 8. Batch Update for Downstream Sync
    @Query("UPDATE user_interactions SET syncStatus = 'SYNCED' WHERE itemId IN (:ids) AND interactionType = :type")
    suspend fun markBatchAsSynced(ids: List<String>, type: String)

    @Query("DELETE FROM user_interactions WHERE itemId IN (:ids) AND interactionType = :type AND syncStatus = 'PENDING_DELETE'")
    suspend fun deleteBatchPending(ids: List<String>, type: String)

    // --- DOWNLOAD SPECIFIC UPDATES ---

    @Query("UPDATE user_interactions SET downloadStatus = :status WHERE itemId = :itemId AND interactionType = 'DOWNLOAD'")
    suspend fun updateDownloadStatus(itemId: String, status: String)

    @Query("UPDATE user_interactions SET downloadProgress = :progress WHERE itemId = :itemId AND interactionType = 'DOWNLOAD'")
    suspend fun updateDownloadProgress(itemId: String, progress: Int)

    @Query("UPDATE user_interactions SET localFilePath = :path, downloadStatus = 'COMPLETED', downloadProgress = 100 WHERE itemId = :itemId AND interactionType = 'DOWNLOAD'")
    suspend fun completeDownload(itemId: String, path: String)

    @Query("SELECT * FROM user_interactions WHERE interactionType = 'DOWNLOAD'")
    suspend fun getAllDownloadsOneShot(): List<UserInteractionEntity>

    @Query("SELECT * FROM user_interactions WHERE itemId = :itemId AND interactionType = 'DOWNLOAD'")
    suspend fun getDownloadInteraction(itemId: String): UserInteractionEntity?

}