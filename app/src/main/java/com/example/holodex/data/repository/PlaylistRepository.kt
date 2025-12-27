package com.example.holodex.data.repository

import androidx.room.withTransaction
import com.example.holodex.auth.TokenManager
import com.example.holodex.background.SyncLogger
import com.example.holodex.data.api.AuthenticatedMusicdexApiService
import com.example.holodex.data.api.HolodexApiService
import com.example.holodex.data.api.MusicdexApiService
import com.example.holodex.data.api.PaginatedChannelsResponse
import com.example.holodex.data.api.PlaylistListResponse
import com.example.holodex.data.api.PlaylistUpdateRequest
import com.example.holodex.data.api.StarPlaylistRequest
import com.example.holodex.data.db.AppDatabase
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.data.db.PlaylistDao
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.data.db.PlaylistItemEntity
import com.example.holodex.data.db.StarredPlaylistDao
import com.example.holodex.data.db.StarredPlaylistEntity
import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.db.mappers.toEntity
import com.example.holodex.data.model.HolodexChannelMin
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.discovery.FullPlaylist
import com.example.holodex.data.model.discovery.MusicdexSong
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    val holodexApiService: HolodexApiService,
    private val musicdexApiService: MusicdexApiService,
    private val authenticatedMusicdexApiService: AuthenticatedMusicdexApiService,
    val playlistDao: PlaylistDao,
    private val appDatabase: AppDatabase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val starredPlaylistDao: StarredPlaylistDao,
    private val tokenManager: TokenManager
) {

    companion object {
        private const val TAG = "PlaylistRepository"
        private val TAG_SYNC = "SYNC_DEBUG"
    }

    fun getStarredPlaylistsFlow(): Flow<List<StarredPlaylistEntity>> {
        return starredPlaylistDao.getStarredPlaylists()
    }

    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        playlistDao.getItemsForPlaylist(playlistId)

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    suspend fun getLastItemOrderInPlaylist(playlistId: Long): Int? =
        withContext(defaultDispatcher) { playlistDao.getLastItemOrder(playlistId) }

    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity? =
        withContext(defaultDispatcher) { playlistDao.getPlaylistById(playlistId) }

    // --- RESTORED HELPER METHODS ---
    suspend fun getPlaylistItemCount(localPlaylistId: Long): Int {
        // .first() gets the current snapshot of the flow
        return playlistDao.getItemsForPlaylist(localPlaylistId).first().size
    }

    suspend fun getLocalOnlyItemCount(localPlaylistId: Long): Int {
        return playlistDao.getItemsForPlaylist(localPlaylistId).first().count { it.isLocalOnly }
    }
    // -------------------------------

    suspend fun getHotSongsForCarousel(org: String?): Result<List<MusicdexSong>> =
        withContext(defaultDispatcher) {
            try {
                val response = holodexApiService.getHotSongs(organization = org, channelId = null)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(IOException("API Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getFullPlaylistContent(playlistId: String): Result<FullPlaylist> =
        withContext(defaultDispatcher) {
            try {
                val isSystemPlaylist = playlistId.startsWith(":")
                val response = if (isSystemPlaylist) {
                    authenticatedMusicdexApiService.getPlaylistContent(playlistId)
                } else {
                    musicdexApiService.getPlaylistContent(playlistId)
                }

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(IOException("API Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getOrgChannelsPaginated(
        org: String, offset: Int, limit: Int = 25
    ): Result<PaginatedChannelsResponse> {
        return try {
            val response = holodexApiService.getChannels(organization = org, offset = offset, limit = limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(PaginatedChannelsResponse(null, response.body()!!))
            } else {
                Result.failure(IOException("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRadioContent(radioId: String): Result<FullPlaylist> =
        withContext(defaultDispatcher) {
            try {
                val response = musicdexApiService.getRadioContent(radioId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(IOException("API Error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getOrgPlaylistsPaginated(
        org: String, type: String, offset: Int, limit: Int = 25
    ): Result<PlaylistListResponse> {
        return try {
            val response = musicdexApiService.getOrgPlaylists(
                org = org.ifBlank { "All_Vtubers" },
                type = type,
                offset = offset,
                limit = limit
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchVideoAndFindSong(
        videoId: String,
        startTime: Int
    ): Pair<HolodexVideoItem, MusicdexSong?>? {
        val sgpId = withContext(Dispatchers.IO) {
            URLEncoder.encode(":video[id=$videoId]", StandardCharsets.UTF_8.toString())
        }
        val result = musicdexApiService.getPlaylistContent(sgpId)
        if (result.isSuccessful && result.body() != null) {
            val fullPlaylist = result.body()!!
            val matchingSong = fullPlaylist.content?.find { it.start == startTime }

            val videoItemShell = HolodexVideoItem(
                id = videoId,
                title = fullPlaylist.title,
                description = fullPlaylist.description,
                type = "stream",
                topicId = null,
                availableAt = matchingSong?.available_at?.toString() ?: "",
                publishedAt = null,
                duration = 0,
                status = "past",
                channel = HolodexChannelMin(
                    id = matchingSong?.channel?.id ?: matchingSong?.channelId,
                    name = matchingSong?.channel?.name ?: "Unknown",
                    englishName = matchingSong?.channel?.englishName,
                    org = null,
                    type = "vtuber",
                    photoUrl = matchingSong?.channel?.photoUrl
                ),
                songcount = fullPlaylist.content?.size,
                songs = fullPlaylist.content?.map { it.toHolodexSong() }
            )
            return Pair(videoItemShell, matchingSong)
        }
        return null
    }

    // --- Playlists Logic (CRUD & Sync Ops) ---
    suspend fun getLocalPlaylists(): List<PlaylistEntity> = playlistDao.getAllPlaylistsOnce()
    suspend fun getLocalPlaylistsByStatus(status: SyncStatus): List<PlaylistEntity> =
        playlistDao.getUnsyncedPlaylists().filter { it.syncStatus == status }

    suspend fun performUpstreamPlaylistDeletions(logger: SyncLogger) {
        val pendingDeletes = getLocalPlaylistsByStatus(SyncStatus.PENDING_DELETE)
        pendingDeletes.forEach { playlist ->
            if (playlist.serverId != null) {
                val response = authenticatedMusicdexApiService.deletePlaylist(playlist.serverId!!)
                if (response.isSuccessful || response.code() == 404) {
                    playlistDao.deletePlaylist(playlist.playlistId)
                }
            } else {
                playlistDao.deletePlaylist(playlist.playlistId)
            }
        }
    }

    suspend fun performUpstreamPlaylistUpserts(logger: SyncLogger) {
        val dirtyPlaylists = getLocalPlaylistsByStatus(SyncStatus.DIRTY)
        val userId = tokenManager.getUserId()?.toLongOrNull() ?: return

        dirtyPlaylists.forEach { playlist ->
            val songServerIds = getLocalPlaylistItemServerIds(playlist.playlistId)
            val requestDto = PlaylistUpdateRequest(
                id = playlist.serverId,
                owner = userId,
                title = playlist.name,
                description = playlist.description,
                content = songServerIds
            )
            val response = authenticatedMusicdexApiService.createOrUpdatePlaylist(requestDto)

            if (response.isSuccessful) {
                if (playlist.serverId != null) {
                    playlistDao.updatePlaylist(playlist.copy(syncStatus = SyncStatus.SYNCED))
                } else {
                    val newServerPlaylist = response.body()?.firstOrNull()
                    if (newServerPlaylist != null) {
                        val finalEntity = newServerPlaylist.toEntity().copy(
                            playlistId = playlist.playlistId,
                            syncStatus = SyncStatus.SYNCED
                        )
                        playlistDao.updatePlaylist(finalEntity)
                    }
                }
            }
        }
    }

    suspend fun getRemotePlaylists(): List<PlaylistEntity> {
        val dtoList = authenticatedMusicdexApiService.getMyPlaylists().body() ?: emptyList()
        return dtoList.map { it.toEntity() }
    }

    suspend fun insertNewSyncedPlaylists(playlists: List<PlaylistEntity>) {
        playlistDao.upsertPlaylists(playlists.map { it.copy(syncStatus = SyncStatus.SYNCED) })
    }

    suspend fun deleteLocalPlaylists(localIds: List<Long>) =
        localIds.forEach { playlistDao.deletePlaylist(it) }

    suspend fun updateLocalPlaylistMetadata(localId: Long, remotePlaylist: PlaylistEntity) {
        playlistDao.updatePlaylistMetadata(
            playlistId = localId,
            name = remotePlaylist.name,
            description = remotePlaylist.description,
            timestamp = remotePlaylist.last_modified_at
        )
    }

    suspend fun getRemotePlaylistContent(serverId: String): List<MusicdexSong> =
        authenticatedMusicdexApiService.getPlaylistContent(serverId).body()?.content ?: emptyList()

    suspend fun getLocalPlaylistItemServerIds(localPlaylistId: Long): List<String> =
        coroutineScope {
            val items = playlistDao.getItemsForPlaylist(localPlaylistId).first()
            val syncedItems = items.filter { !it.isLocalOnly }
            syncedItems.map { playlistItem ->
                async {
                    playlistItem.songStartSecondsPlaylist?.let { startTime ->
                        fetchVideoAndFindSong(playlistItem.videoIdForItem, startTime)?.second?.id
                    }
                }
            }.awaitAll().filterNotNull()
        }

    suspend fun reconcileLocalPlaylistItems(localPlaylistId: Long, remoteSongs: List<MusicdexSong>) {
        appDatabase.withTransaction {
            val localItems = playlistDao.getItemsForPlaylist(localPlaylistId).first()
            val localOnlyItems = localItems.filter { it.isLocalOnly }

            val remoteItemEntities = remoteSongs.mapIndexedNotNull { index, song ->
                if (song.channelId.isNullOrBlank()) return@mapIndexedNotNull null
                val compositeId = "${song.videoId}_${song.start}"
                PlaylistItemEntity(
                    playlistOwnerId = localPlaylistId,
                    itemIdInPlaylist = compositeId,
                    videoIdForItem = song.videoId,
                    itemTypeInPlaylist = LikedItemType.SONG_SEGMENT,
                    songStartSecondsPlaylist = song.start,
                    songEndSecondsPlaylist = song.end,
                    songNamePlaylist = song.name,
                    songArtistTextPlaylist = song.channel.name,
                    songArtworkUrlPlaylist = song.artUrl,
                    itemOrder = index,
                    syncStatus = SyncStatus.SYNCED,
                    isLocalOnly = false
                )
            }

            val finalMergedList = remoteItemEntities.toMutableList()
            finalMergedList.addAll(localOnlyItems)
            val finalListWithCorrectOrder = finalMergedList.mapIndexed { index, item ->
                item.copy(itemOrder = index)
            }

            playlistDao.deleteAllItemsForPlaylist(localPlaylistId)
            if (finalListWithCorrectOrder.isNotEmpty()) {
                playlistDao.upsertPlaylistItems(finalListWithCorrectOrder)
            }
        }
    }

    suspend fun savePlaylistEdits(editedPlaylist: PlaylistEntity, finalItems: List<PlaylistItemEntity>) =
        withContext(defaultDispatcher) {
            val itemsToSave = finalItems.mapIndexed { index, item -> item.copy(itemOrder = index) }
            playlistDao.updatePlaylistAndItems(editedPlaylist, itemsToSave)
        }

    suspend fun createNewPlaylist(name: String, description: String? = null): Long =
        withContext(defaultDispatcher) {
            val now = Instant.now().toString()
            val userId = tokenManager.getUserId() ?: throw IOException("User not logged in.")
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = name.trim(),
                    description = description?.trim(),
                    syncStatus = SyncStatus.DIRTY,
                    owner = userId.toLongOrNull(),
                    createdAt = now,
                    last_modified_at = now,
                    isDeleted = false,
                    serverId = null
                )
            )
        }

    suspend fun deletePlaylist(playlistId: Long) = withContext(defaultDispatcher) {
        playlistDao.softDeletePlaylist(playlistId)
    }

    suspend fun addPlaylistItem(playlistItem: PlaylistItemEntity) = withContext(defaultDispatcher) {
        appDatabase.withTransaction {
            if (!playlistItem.isLocalOnly) {
                val parentPlaylist = playlistDao.getPlaylistById(playlistItem.playlistOwnerId)
                if (parentPlaylist != null) {
                    playlistDao.updatePlaylist(
                        parentPlaylist.copy(syncStatus = SyncStatus.DIRTY, last_modified_at = Instant.now().toString())
                    )
                }
            }
            playlistDao.insertPlaylistItem(playlistItem)
        }
    }

    suspend fun getLastItemOrder(playlistId: Long) = playlistDao.getLastItemOrder(playlistId)

    // --- Starred Playlists ---
    suspend fun performUpstreamStarredPlaylistsSync(logger: SyncLogger) {
        val toRemove = starredPlaylistDao.getUnsyncedItems().filter { it.syncStatus == SyncStatus.PENDING_DELETE }
        toRemove.forEach {
            if (authenticatedMusicdexApiService.unstarPlaylist(StarPlaylistRequest(it.playlistId)).isSuccessful) {
                starredPlaylistDao.deleteById(it.playlistId)
            }
        }
        val toAdd = starredPlaylistDao.getUnsyncedItems().filter { it.syncStatus == SyncStatus.DIRTY }
        toAdd.forEach {
            if (authenticatedMusicdexApiService.starPlaylist(StarPlaylistRequest(it.playlistId)).isSuccessful) {
                starredPlaylistDao.insert(it.copy(syncStatus = SyncStatus.SYNCED))
            }
        }
    }

    suspend fun getRemoteStarredPlaylists(): List<PlaylistStub> =
        authenticatedMusicdexApiService.getStarredPlaylists().body() ?: emptyList()

    suspend fun getLocalUnsyncedStarredPlaylistsCount(): Int = starredPlaylistDao.getUnsyncedItems().size

    suspend fun deleteLocalSyncedStarredPlaylists(): Int {
        val count = starredPlaylistDao.getStarredPlaylists().first().count { it.syncStatus == SyncStatus.SYNCED }
        starredPlaylistDao.deleteAllSyncedItems()
        return count
    }

    suspend fun insertRemoteStarredPlaylistsAsSynced(starred: List<PlaylistStub>) {
        val entities = starred.map { StarredPlaylistEntity(it.id, SyncStatus.SYNCED) }
        starredPlaylistDao.upsertAll(entities)
    }

    private fun MusicdexSong.toHolodexSong(): HolodexSong {
        return HolodexSong(
            name = this.name,
            start = this.start,
            end = this.end,
            itunesId = null,
            artUrl = this.artUrl,
            originalArtist = this.originalArtist,
            videoId = this.videoId
        )
    }
}