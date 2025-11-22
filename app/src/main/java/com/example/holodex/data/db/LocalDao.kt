// File: java/com/example/holodex/data/db/LocalDao.kt

package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDao {

    // --- Local Favorites ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addLocalFavorite(favorite: LocalFavoriteEntity)

    @Query("DELETE FROM local_favorites WHERE itemId = :itemId")
    suspend fun removeLocalFavorite(itemId: String)

    @Query("SELECT * FROM local_favorites")
    fun getLocalFavorites(): Flow<List<LocalFavoriteEntity>>

    @Query("SELECT itemId FROM local_favorites")
    fun getLocalFavoriteIds(): Flow<List<String>>

    // --- External Channels ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addExternalChannel(channel: ExternalChannelEntity)

    @Query("DELETE FROM external_channels WHERE channelId = :channelId")
    suspend fun removeExternalChannel(channelId: String)

    @Query("SELECT * FROM external_channels")
    fun getAllExternalChannels(): Flow<List<ExternalChannelEntity>>

    @Query("SELECT * FROM external_channels WHERE channelId = :channelId")
    suspend fun getExternalChannel(channelId: String): ExternalChannelEntity?

    // --- Local Playlists (NEW) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createLocalPlaylist(playlist: LocalPlaylistEntity): Long

    @Query("SELECT * FROM local_playlists")
    fun getAllLocalPlaylists(): Flow<List<LocalPlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToLocalPlaylist(item: LocalPlaylistItemEntity)

    @Query("SELECT * FROM local_playlist_items WHERE playlistOwnerId = :playlistId ORDER BY itemOrder ASC")
    fun getItemsForLocalPlaylist(playlistId: Long): Flow<List<LocalPlaylistItemEntity>>
}