package com.example.holodex.data.repository

import com.example.holodex.data.db.ExternalChannelEntity
import com.example.holodex.data.db.LocalDao
import com.example.holodex.data.db.LocalFavoriteEntity
import com.example.holodex.data.db.LocalPlaylistEntity
import com.example.holodex.data.db.LocalPlaylistItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository dedicated to managing all local-only data.
 * This repository is NOT sync-aware and operates independently from HolodexRepository.
 */
@Singleton
class LocalRepository @Inject constructor(
    private val localDao: LocalDao
) {

    // --- Local Favorites ---
    suspend fun addLocalFavorite(favorite: LocalFavoriteEntity) = localDao.addLocalFavorite(favorite)
    suspend fun removeLocalFavorite(itemId: String) = localDao.removeLocalFavorite(itemId)
    fun getLocalFavorites(): Flow<List<LocalFavoriteEntity>> = localDao.getLocalFavorites()
    fun getLocalFavoriteIds(): Flow<List<String>> = localDao.getLocalFavoriteIds()

    // --- External Channels ---
    suspend fun addExternalChannel(channel: ExternalChannelEntity) = localDao.addExternalChannel(channel)
    suspend fun removeExternalChannel(channelId: String) = localDao.removeExternalChannel(channelId)
    fun getAllExternalChannels(): Flow<List<ExternalChannelEntity>> = localDao.getAllExternalChannels()
    suspend fun getExternalChannel(channelId: String): ExternalChannelEntity? = localDao.getExternalChannel(channelId)

    // --- Local Playlists ---
    suspend fun createLocalPlaylist(playlist: LocalPlaylistEntity): Long = localDao.createLocalPlaylist(playlist)
    fun getAllLocalPlaylists(): Flow<List<LocalPlaylistEntity>> = localDao.getAllLocalPlaylists()
    suspend fun addSongToLocalPlaylist(item: LocalPlaylistItemEntity) = localDao.addSongToLocalPlaylist(item)
    fun getItemsForLocalPlaylist(playlistId: Long): Flow<List<LocalPlaylistItemEntity>> = localDao.getItemsForLocalPlaylist(playlistId)
}