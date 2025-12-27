// File: java/com/example/holodex/data/repository/HolodexRepository.kt
package com.example.holodex.data.repository

import androidx.media3.common.util.UnstableApi
import androidx.room.withTransaction
import com.example.holodex.auth.TokenManager
import com.example.holodex.background.LogAction
import com.example.holodex.background.SyncLogger
import com.example.holodex.data.api.AuthenticatedMusicdexApiService
import com.example.holodex.data.api.HolodexApiService
import com.example.holodex.data.api.MusicdexApiService
import com.example.holodex.data.api.Organization
import com.example.holodex.data.api.PaginatedChannelsResponse
import com.example.holodex.data.api.PlaylistListResponse
import com.example.holodex.data.api.PlaylistUpdateRequest
import com.example.holodex.data.api.StarPlaylistRequest
import com.example.holodex.data.db.AppDatabase
import com.example.holodex.data.db.DiscoveryDao
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.data.db.PlaylistDao
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.data.db.PlaylistItemEntity
import com.example.holodex.data.db.StarredPlaylistDao
import com.example.holodex.data.db.StarredPlaylistEntity
import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.mappers.toEntity
import com.example.holodex.data.model.HolodexChannelMin
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.discovery.DiscoveryResponse
import com.example.holodex.data.model.discovery.FullPlaylist
import com.example.holodex.data.model.discovery.MusicdexSong
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.di.ApplicationScope
import com.example.holodex.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class HolodexRepository @Inject constructor(
    val holodexApiService: HolodexApiService,
    private val musicdexApiService: MusicdexApiService,
    private val authenticatedMusicdexApiService: AuthenticatedMusicdexApiService,
    private val discoveryDao: DiscoveryDao,
    val playlistDao: PlaylistDao,
    private val appDatabase: AppDatabase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher, // Use Qualifier
    private val starredPlaylistDao: StarredPlaylistDao,
    private val tokenManager: TokenManager,
    private val unifiedDao: UnifiedDao,
    // Add here
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    companion object {
        private const val TAG = "HolodexRepository"
        private val TAG_SYNC = "SYNC_DEBUG"
    }

    private val _availableOrganizations = MutableStateFlow<List<Pair<String, String?>>>(
        listOf("All Vtubers" to null, "Favorites" to "Favorites") // Initial default value
    )
    val availableOrganizations: StateFlow<List<Pair<String, String?>>> =
        _availableOrganizations.asStateFlow()

    init {


        // Fetch the dynamic organization list as soon as the repository is created.
        applicationScope.launch {
            fetchOrganizationList()
        }
    }

    private suspend fun fetchOrganizationList() {
        getOrganizationList() // This is the existing function that calls the API
            .onSuccess { orgs ->
                val orgList = orgs.map { org -> (org.name to org.name) }
                // Prepend the static options to the dynamic list
                _availableOrganizations.value =
                    listOf("All Vtubers" to null, "Favorites" to "Favorites") + orgList
            }
            .onFailure {
                Timber.e(it, "Failed to load dynamic organization list. Using hardcoded fallback.")
                // Populate with a fallback list on failure
                _availableOrganizations.value = listOf(
                    "All Vtubers" to null,
                    "Favorites" to "Favorites",
                    "Hololive" to "Hololive",
                    "Nijisanji" to "Nijisanji",
                    "Independents" to "Independents"
                )
            }
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





    suspend fun getHotSongsForCarousel(org: String?): Result<List<MusicdexSong>> =
        withContext(defaultDispatcher) {
            try {
                val response = holodexApiService.getHotSongs(organization = org, channelId = null)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(IOException("API Error fetching hot songs for org '$org': ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }



    suspend fun getFullPlaylistContent(playlistId: String): Result<FullPlaylist> =
        withContext(defaultDispatcher) {
            try {
                Timber.d("$TAG: Fetching full playlist content from network for ID: $playlistId")

                val isSystemPlaylist = playlistId.startsWith(":")

                val response = if (isSystemPlaylist) {
                    // System playlists like :history and :video require authentication
                    authenticatedMusicdexApiService.getPlaylistContent(playlistId)
                } else {
                    musicdexApiService.getPlaylistContent(playlistId)
                }

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(IOException("API Error fetching playlist content for '$playlistId': ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }




    suspend fun getOrgChannelsPaginated(
        org: String,
        offset: Int,
        limit: Int = 25
    ): Result<PaginatedChannelsResponse> { // The return type to the ViewModel remains the same for consistency
        return try {
            val response = holodexApiService.getChannels(
                organization = org,
                offset = offset,
                limit = limit
            )
            if (response.isSuccessful && response.body() != null) {
                val channelsList = response.body()!!
                // Manually construct the PaginatedChannelsResponse object.
                // The 'total' will be null, but the view model already handles this.
                val paginatedResponse = PaginatedChannelsResponse(
                    total = null, // The API doesn't provide a total in this format
                    items = channelsList
                )
                Result.success(paginatedResponse)
            } else {
                Result.failure(IOException("API Error fetching org channels for '$org': ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch org channels for: $org")
            Result.failure(e)
        }
    }

    suspend fun getRadioContent(radioId: String): Result<FullPlaylist> =
        withContext(defaultDispatcher) {
            try {
                Timber.d("$TAG: Fetching radio content from network for ID: $radioId")
                val response = musicdexApiService.getRadioContent(radioId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(IOException("API Error fetching radio content for '$radioId': ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getOrgPlaylistsPaginated(
        org: String,
        type: String,
        offset: Int,
        limit: Int = 25
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
                Result.failure(IOException("API Error fetching org playlists (type: $type): ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to fetch org playlists for org: $org, type: $type")
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


    // --- Playlists ---
    suspend fun getLocalPlaylists(): List<PlaylistEntity> = playlistDao.getAllPlaylistsOnce()
    suspend fun getLocalPlaylistsByStatus(status: SyncStatus): List<PlaylistEntity> =
        playlistDao.getUnsyncedPlaylists().filter { it.syncStatus == status }

    suspend fun performUpstreamPlaylistDeletions(logger: SyncLogger) {
        val pendingDeletes = getLocalPlaylistsByStatus(SyncStatus.PENDING_DELETE)
        if (pendingDeletes.isNotEmpty()) logger.info("  Processing ${pendingDeletes.size} pending deletions...")
        pendingDeletes.forEach { playlist ->
            if (playlist.serverId != null) {
                val response = authenticatedMusicdexApiService.deletePlaylist(playlist.serverId!!)
                if (response.isSuccessful || response.code() == 404) {
                    logger.logItemAction(
                        LogAction.UPSTREAM_DELETE_SUCCESS,
                        playlist.name,
                        playlist.playlistId,
                        playlist.serverId
                    )
                    playlistDao.deletePlaylist(playlist.playlistId)
                } else {
                    logger.logItemAction(
                        LogAction.UPSTREAM_DELETE_FAILED,
                        playlist.name,
                        playlist.playlistId,
                        playlist.serverId,
                        "Code: ${response.code()}"
                    )
                }
            } else {
                playlistDao.deletePlaylist(playlist.playlistId) // Local-only item
            }
        }
    }

    suspend fun performUpstreamPlaylistUpserts(logger: SyncLogger) {
        val dirtyPlaylists = getLocalPlaylistsByStatus(SyncStatus.DIRTY)
        val userId = tokenManager.getUserId()?.toLongOrNull() ?: return
        if (dirtyPlaylists.isNotEmpty()) logger.info("  Processing ${dirtyPlaylists.size} dirty playlists...")

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
                    logger.logItemAction(
                        LogAction.UPSTREAM_UPSERT_SUCCESS,
                        playlist.name,
                        playlist.playlistId,
                        playlist.serverId
                    )
                    val updatedEntity = playlist.copy(syncStatus = SyncStatus.SYNCED)
                    playlistDao.updatePlaylist(updatedEntity)
                } else {
                    val newServerPlaylist = response.body()?.firstOrNull()
                    if (newServerPlaylist != null) {
                        logger.logItemAction(
                            LogAction.UPSTREAM_UPSERT_SUCCESS,
                            newServerPlaylist.title,
                            playlist.playlistId,
                            newServerPlaylist.id
                        )
                        val finalEntity = newServerPlaylist.toEntity().copy(
                            playlistId = playlist.playlistId, // Keep the original local ID
                            syncStatus = SyncStatus.SYNCED
                        )
                        playlistDao.updatePlaylist(finalEntity)
                    } else {
                        logger.logItemAction(
                            LogAction.UPSTREAM_UPSERT_FAILED,
                            playlist.name,
                            playlist.playlistId,
                            null,
                            "Server returned success but no playlist data."
                        )
                    }
                }
            } else {
                logger.logItemAction(
                    LogAction.UPSTREAM_UPSERT_FAILED,
                    playlist.name,
                    playlist.playlistId,
                    playlist.serverId,
                    "Code: ${response.code()}"
                )
            }
        }
    }

    suspend fun getRemotePlaylists(): List<PlaylistEntity> {
        val dtoList = authenticatedMusicdexApiService.getMyPlaylists().body() ?: emptyList()
        return dtoList.map { it.toEntity() } // Map the whole list
    }

    suspend fun insertNewSyncedPlaylists(playlists: List<PlaylistEntity>) {
        playlistDao.upsertPlaylists(playlists.map { it.copy(syncStatus = SyncStatus.SYNCED) })
    }

    suspend fun deleteLocalPlaylists(localIds: List<Long>) =
        localIds.forEach { playlistDao.deletePlaylist(it) }

    suspend fun updateLocalPlaylistMetadata(localId: Long, remotePlaylist: PlaylistEntity) {
        // Use the surgical UPDATE query - this should NOT trigger CASCADE
        playlistDao.updatePlaylistMetadata(
            playlistId = localId,
            name = remotePlaylist.name,
            description = remotePlaylist.description,
            timestamp = remotePlaylist.last_modified_at
        )

        // Diagnostic: Verify items still exist after metadata update
        val itemsAfterUpdate = playlistDao.getItemsForPlaylist(localId).first()
        Timber.tag("SYNC_DEBUG").i(
            "After updateLocalPlaylistMetadata: Playlist $localId has ${itemsAfterUpdate.size} items"
        )
    }


    suspend fun getRemotePlaylistContent(serverId: String): List<MusicdexSong> =
        authenticatedMusicdexApiService.getPlaylistContent(serverId).body()?.content ?: emptyList()

    // --- MODIFIED TO FILTER LOCAL-ONLY ITEMS ---
    suspend fun getLocalPlaylistItemServerIds(localPlaylistId: Long): List<String> =
        coroutineScope {
            val items = playlistDao.getItemsForPlaylist(localPlaylistId).first()

            // *** THE CRITICAL FILTER ***
            // Only consider items that are meant to be synced to the server.
            val syncedItems = items.filter { !it.isLocalOnly }

            // Asynchronously fetch the server ID for each item in the playlist.
            // This is necessary because the playlist only stores a reference (videoId + startTime),
            // not the song's unique server UUID needed for the sync.
            syncedItems.map { playlistItem ->
                async {
                    playlistItem.songStartSecondsPlaylist?.let { startTime ->
                        // This helper function fetches the video details from the API and finds the
                        // song with the matching start time to get its server ID.
                        fetchVideoAndFindSong(playlistItem.videoIdForItem, startTime)?.second?.id
                    }
                }
            }.awaitAll()
                .filterNotNull() // Launch all lookups in parallel, wait for them, and filter out any failures
        }
    // --- END OF MODIFICATION ---

    suspend fun reconcileLocalPlaylistItems(
        localPlaylistId: Long,
        remoteSongs: List<MusicdexSong>
    ) {
        appDatabase.withTransaction {
            // IMPORTANT: Read items at the START of the transaction to get fresh data
            // Using .first() on a Flow inside a transaction should give us the current state
            val localItems = playlistDao.getItemsForPlaylist(localPlaylistId).first()

            Timber.tag("SYNC_DEBUG").i(
                "reconcileLocalPlaylistItems START: Found ${localItems.size} local items for playlist $localPlaylistId"
            )

            val localOnlyItems = localItems.filter { it.isLocalOnly }

            Timber.tag("SYNC_DEBUG").i(
                "reconcileLocalPlaylistItems: Filtered ${localOnlyItems.size} local-only items to preserve"
            )

            // --- Step 2: Prepare the "Server Truth" ---
            val remoteItemEntities = remoteSongs.mapIndexedNotNull { index, song ->
                if (song.channelId.isNullOrBlank()) {
                    Timber.w("Skipping playlist song ('${song.name}') because its top-level 'channel_id' is missing.")
                    return@mapIndexedNotNull null
                }

                // FIX: Manually construct the ID string.
                // LikedItemEntity.generateSongItemId(song.videoId, song.start) was just this:
                val compositeId = "${song.videoId}_${song.start}"

                PlaylistItemEntity(
                    playlistOwnerId = localPlaylistId,
                    itemIdInPlaylist = compositeId, // <--- Fixed here
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

            // --- Step 3: Construct the new "Final Truth" by merging ---
            val finalMergedList = remoteItemEntities.toMutableList()
            finalMergedList.addAll(localOnlyItems)
            val finalListWithCorrectOrder = finalMergedList.mapIndexed { index, item ->
                item.copy(itemOrder = index)
            }

            // --- Step 4: Execute Database Operations ---
            // Delete all items first, then insert the merged list
            playlistDao.deleteAllItemsForPlaylist(localPlaylistId)

            if (finalListWithCorrectOrder.isNotEmpty()) {
                playlistDao.upsertPlaylistItems(finalListWithCorrectOrder)
            }

            Timber.tag("SYNC_DEBUG").i(
                "Reconciled playlist ID $localPlaylistId. Kept ${localOnlyItems.size} local-only items. Final total: ${finalListWithCorrectOrder.size} items."
            )
        }
    }

    suspend fun savePlaylistEdits(
        editedPlaylist: PlaylistEntity,
        finalItems: List<PlaylistItemEntity>
    ) = withContext(defaultDispatcher) {
        val itemsToSave = finalItems.mapIndexed { index, item ->
            item.copy(itemOrder = index)
        }

        playlistDao.updatePlaylistAndItems(editedPlaylist, itemsToSave)

        if (editedPlaylist.syncStatus == SyncStatus.DIRTY) {
            Timber.tag(TAG_SYNC)
                .i("Saved edits for playlist '${editedPlaylist.name}'. Marked as DIRTY for next sync.")
        } else {
            Timber.tag(TAG_SYNC)
                .i("Saved local-only edits for playlist '${editedPlaylist.name}'. Sync status remains SYNCED.")
        }
    }

    suspend fun createNewPlaylist(name: String, description: String? = null): Long =
        withContext(defaultDispatcher) {
            val now = Instant.now().toString()
            val userId = tokenManager.getUserId()
            if (userId == null) {
                Timber.e("Cannot create playlist: User is not logged in.")
                throw IOException("User not logged in.")
            }
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
            // Only mark the playlist as dirty if the item being added is a syncable item.
            if (!playlistItem.isLocalOnly) {
                val parentPlaylist = playlistDao.getPlaylistById(playlistItem.playlistOwnerId)
                if (parentPlaylist != null) {
                    playlistDao.updatePlaylist(
                        parentPlaylist.copy(
                            syncStatus = SyncStatus.DIRTY,
                            last_modified_at = Instant.now().toString()
                        )
                    )
                }
            }

            // Always insert the new item, whether it's local or not.
            playlistDao.insertPlaylistItem(playlistItem)
        }
    }

    // Add these helper methods for diagnostics
    suspend fun getPlaylistItemCount(localPlaylistId: Long): Int {
        return playlistDao.getItemsForPlaylist(localPlaylistId).first().size
    }

    suspend fun getLocalOnlyItemCount(localPlaylistId: Long): Int {
        return playlistDao.getItemsForPlaylist(localPlaylistId).first().count { it.isLocalOnly }
    }

    // --- Starred Playlists ---
    suspend fun performUpstreamStarredPlaylistsSync(logger: SyncLogger) {
        val toRemove = starredPlaylistDao.getUnsyncedItems()
            .filter { it.syncStatus == SyncStatus.PENDING_DELETE }
        toRemove.forEach {
            val response =
                authenticatedMusicdexApiService.unstarPlaylist(StarPlaylistRequest(it.playlistId))
            if (response.isSuccessful) {
                logger.info("  -> Successfully UNSTARRED playlist ${it.playlistId} on server.")
                starredPlaylistDao.deleteById(it.playlistId)
            } else {
                logger.warning("  -> FAILED to unstar playlist ${it.playlistId}. Code: ${response.code()}")
            }
        }

        val toAdd =
            starredPlaylistDao.getUnsyncedItems().filter { it.syncStatus == SyncStatus.DIRTY }
        toAdd.forEach {
            val response =
                authenticatedMusicdexApiService.starPlaylist(StarPlaylistRequest(it.playlistId))
            if (response.isSuccessful) {
                logger.info("  -> Successfully STARRED playlist ${it.playlistId} on server.")
                starredPlaylistDao.insert(it.copy(syncStatus = SyncStatus.SYNCED))
            } else {
                logger.warning("  -> FAILED to star playlist ${it.playlistId}. Code: ${response.code()}")
            }
        }
    }

    suspend fun getRemoteStarredPlaylists(): List<PlaylistStub> =
        authenticatedMusicdexApiService.getStarredPlaylists().body() ?: emptyList()

    suspend fun getLocalUnsyncedStarredPlaylistsCount(): Int =
        starredPlaylistDao.getUnsyncedItems().size

    suspend fun deleteLocalSyncedStarredPlaylists(): Int {
        val syncedItems = starredPlaylistDao.getStarredPlaylists().first()
            .filter { it.syncStatus == SyncStatus.SYNCED }
        if (syncedItems.isNotEmpty()) {
            starredPlaylistDao.deleteAllSyncedItems() // Assuming this method deletes where syncStatus is SYNCED
        }
        return syncedItems.size
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


    suspend fun searchForExternalChannels(query: String): Result<List<com.example.holodex.data.model.ChannelSearchResult>> =
        withContext(defaultDispatcher) {
            try {
                val ytService = NewPipe.getService(ServiceList.YouTube.serviceId)
                val extractor = ytService.getSearchExtractor(query, listOf("channels"), "")
                extractor.fetchPage()

                val results =
                    extractor.initialPage.items.mapNotNull { it as? org.schabi.newpipe.extractor.channel.ChannelInfoItem }
                        .map { infoItem ->
                            com.example.holodex.data.model.ChannelSearchResult(
                                channelId = infoItem.url.substringAfter("/channel/"),
                                name = infoItem.name,
                                thumbnailUrl = infoItem.thumbnails.firstOrNull()?.url,
                                subscriberCount = if (infoItem.subscriberCount > 0) "${infoItem.subscriberCount} subscribers" else null
                            )
                        }
                Result.success(results)
            } catch (e: Exception) {
                Timber.e(e, "Failed to search for external channels with query: $query")
                Result.failure(e)
            }
        }

    suspend fun getOrganizationList(): Result<List<Organization>> = withContext(defaultDispatcher) {
        try {
            val response = holodexApiService.getOrganizations()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("API Error fetching organizations: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}