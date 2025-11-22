// File: java/com/example/holodex/data/db/PlaylistDao.kt

package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    // --- START OF FIX: Add a dedicated @Update function ---
    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)
    // --- END OF FIX ---

    @Transaction
    suspend fun updatePlaylistAndItems(playlist: PlaylistEntity, items: List<PlaylistItemEntity>) {
        updatePlaylist(playlist)
        deleteAllItemsForPlaylist(playlist.playlistId)
        if (items.isNotEmpty()) {
            upsertPlaylistItems(items)
        }
    }

    @Query("SELECT * FROM playlists WHERE is_deleted = 0 ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("UPDATE playlists SET is_deleted = 1, sync_status = 'PENDING_DELETE' WHERE playlistId = :playlistId")
    suspend fun softDeletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // --- START OF FIX: Change strategy to REPLACE for robustness ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(playlistItem: PlaylistItemEntity)
    // --- END OF FIX ---

    @Query("UPDATE playlist_items SET sync_status = 'PENDING_DELETE' WHERE playlist_owner_id = :playlistId AND item_id_in_playlist = :itemIdInPlaylist")
    suspend fun softDeletePlaylistItem(playlistId: Long, itemIdInPlaylist: String)

    @Query("SELECT * FROM playlist_items WHERE playlist_owner_id = :playlistId AND sync_status != 'PENDING_DELETE' ORDER BY item_order ASC")
    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>>

    // --- START OF FIX: Make query more robust by excluding soft-deleted items ---
    @Query("SELECT MAX(item_order) FROM playlist_items WHERE playlist_owner_id = :playlistId AND sync_status != 'PENDING_DELETE'")
    suspend fun getLastItemOrder(playlistId: Long): Int?
    // --- END OF FIX ---

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE sync_status != 'SYNCED'")
    suspend fun getUnsyncedPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_items WHERE sync_status != 'SYNCED'")
    suspend fun getUnsyncedPlaylistItems(): List<PlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlists WHERE is_deleted = 1")
    suspend fun deleteSoftDeletedPlaylists()

    @Query("DELETE FROM playlist_items WHERE playlist_owner_id = :playlistId")
    suspend fun deleteAllItemsForPlaylist(playlistId: Long)

    @Query("UPDATE playlist_items SET sync_status = 'SYNCED' WHERE playlist_owner_id = :playlistId AND item_id_in_playlist IN (:itemIds)")
    suspend fun markItemsAsSynced(playlistId: Long, itemIds: List<String>)

    @Query("DELETE FROM playlist_items WHERE sync_status = 'PENDING_DELETE' AND playlist_owner_id = :playlistId")
    suspend fun deleteSyncedSoftDeletedItemsForPlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :name, description = :description, updated_at = :timestamp WHERE playlistId = :playlistId")
    suspend fun updatePlaylistMetadata(playlistId: Long, name: String?, description: String?, timestamp: String?)

}