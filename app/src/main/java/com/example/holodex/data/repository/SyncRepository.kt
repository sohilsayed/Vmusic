package com.example.holodex.data.repository

import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.db.UserInteractionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val unifiedDao: UnifiedDao
) {

    // --- GENERIC SYNC HELPERS ---

    suspend fun getDirtyItems(type: String): List<UserInteractionEntity> {
        return unifiedDao.getDirtyInteractions(type)
    }

    suspend fun getPendingDeleteItems(type: String): List<UserInteractionEntity> {
        return unifiedDao.getPendingDeleteInteractions(type)
    }

    suspend fun getSyncedItems(type: String): List<UserInteractionEntity> {
        return unifiedDao.getSyncedItems(type)
    }
    suspend fun getMetadata(id: String): UnifiedMetadataEntity? {
        return unifiedDao.getItemByIdOneShot(id)?.metadata
    }
    /**
     * Called after a successful upstream POST.
     * Updates local item with the Server ID and marks as SYNCED.
     */
    suspend fun markAsSynced(itemId: String, type: String, serverId: String) {
        unifiedDao.confirmUpload(itemId, type, serverId)
    }

    /**
     * Updates the Server ID for an item without changing its sync status to SYNCED.
     * Useful for repairing orphans that need to remain DIRTY for the next sync pass.
     */
    suspend fun updateServerId(itemId: String, type: String, serverId: String) {
        unifiedDao.updateServerId(itemId, type, serverId)
    }

    /**
     * Called after a successful upstream DELETE.
     * Removes the PENDING_DELETE row from the DB.
     */
    suspend fun confirmDeletion(itemId: String, type: String) {
        unifiedDao.confirmDeletion(itemId, type)
    }

    // --- DOWNSTREAM HELPERS ---

    /**
     * Inserts a new item found on the server.
     * NOTE: You must provide metadata because Foreign Keys require it.
     */
    suspend fun insertRemoteItem(
        itemId: String,
        type: String,
        serverId: String,
        metadata: UnifiedMetadataEntity
    ) {
        // 1. Ensure Metadata exists (Upsert prevents crashes if it exists)
        unifiedDao.upsertMetadata(metadata)

        // 2. Insert Interaction
        val interaction = UserInteractionEntity(
            itemId = itemId,
            interactionType = type,
            timestamp = System.currentTimeMillis(), // Or server timestamp if available
            serverId = serverId,
            syncStatus = SyncStatus.SYNCED.name
        )
        unifiedDao.upsertInteraction(interaction)
    }

    /**
     * Removes an item locally because it was deleted on the server.
     * SAFE: Only deletes if status is SYNCED. Ignores DIRTY/PENDING items (User changes win).
     */
    suspend fun removeRemoteItem(itemId: String, type: String) {
        unifiedDao.deleteSyncedItem(itemId, type)
    }

    // --- Batch Operations for Efficiency ---
    suspend fun markBatchSynced(ids: List<String>, type: String) {
        unifiedDao.markBatchAsSynced(ids, type)
    }

    suspend fun confirmBatchDeletion(ids: List<String>, type: String) {
        unifiedDao.deleteBatchPending(ids, type)
    }
}