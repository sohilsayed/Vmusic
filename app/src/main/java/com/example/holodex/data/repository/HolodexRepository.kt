// File: java/com/example/holodex/data/repository/HolodexRepository.kt
package com.example.holodex.data.repository

import androidx.media3.common.util.UnstableApi
import androidx.room.withTransaction
import com.example.holodex.auth.TokenManager
import com.example.holodex.background.LogAction
import com.example.holodex.background.SyncLogger
import com.example.holodex.data.api.AuthenticatedMusicdexApiService
import com.example.holodex.data.api.HolodexApiService
import com.example.holodex.data.api.LatestSongsRequest
import com.example.holodex.data.api.MusicdexApiService
import com.example.holodex.data.api.Organization
import com.example.holodex.data.api.PaginatedChannelsResponse
import com.example.holodex.data.api.PaginatedSongsResponse
import com.example.holodex.data.api.PlaylistListResponse
import com.example.holodex.data.api.PlaylistUpdateRequest
import com.example.holodex.data.api.StarPlaylistRequest
import com.example.holodex.data.cache.BrowseCacheKey
import com.example.holodex.data.cache.BrowseListCache
import com.example.holodex.data.cache.CacheException
import com.example.holodex.data.cache.CachePolicy
import com.example.holodex.data.cache.FetcherResult
import com.example.holodex.data.cache.SearchCacheKey
import com.example.holodex.data.cache.SearchListCache
import com.example.holodex.data.db.AppDatabase
import com.example.holodex.data.db.CachedDiscoveryResponse
import com.example.holodex.data.db.DiscoveryDao
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.data.db.PlaylistDao
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.data.db.PlaylistItemEntity
import com.example.holodex.data.db.StarredPlaylistDao
import com.example.holodex.data.db.StarredPlaylistEntity
import com.example.holodex.data.db.SyncMetadataDao
import com.example.holodex.data.db.SyncStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.VideoDao
import com.example.holodex.data.db.mappers.toEntity
import com.example.holodex.data.db.toEntity
import com.example.holodex.data.model.HolodexChannelMin
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.SearchCondition
import com.example.holodex.data.model.VideoSearchRequest
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.model.discovery.DiscoveryResponse
import com.example.holodex.data.model.discovery.FullPlaylist
import com.example.holodex.data.model.discovery.MusicdexSong
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.di.ApplicationScope
import com.example.holodex.di.DefaultDispatcher
import com.example.holodex.util.VideoFilteringUtil
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.VideoListViewModel
import com.example.holodex.viewmodel.state.BrowseFilterState
import com.example.holodex.viewmodel.state.ViewTypePreset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class HolodexRepository @Inject constructor(
    val holodexApiService: HolodexApiService,
    private val musicdexApiService: MusicdexApiService,
    private val authenticatedMusicdexApiService: AuthenticatedMusicdexApiService,
    private val discoveryDao: DiscoveryDao,
    private val browseListCache: BrowseListCache,
    private val searchListCache: SearchListCache,
    private val videoDao: VideoDao,
    val playlistDao: PlaylistDao,
    private val appDatabase: AppDatabase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher, // Use Qualifier
    internal val syncMetadataDao: SyncMetadataDao,
    private val starredPlaylistDao: StarredPlaylistDao,
    private val tokenManager: TokenManager,
    private val unifiedDao: UnifiedDao,
    private val unifiedRepository: UnifiedVideoRepository, // Add here
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    companion object {
        private const val TAG = "HolodexRepository"
        private val DISCOVERY_CACHE_TTL_MS = TimeUnit.HOURS.toMillis(1)
        const val DEFAULT_PAGE_SIZE = 50
        val DEFAULT_MUSIC_TOPICS =
            listOf("singing", "Music_Cover", "Original_Song", "3D_Stream")
        val CACHE_STALE_DURATION_MS = TimeUnit.HOURS.toMillis(1)
        private val TAG_SYNC = "SYNC_DEBUG"
    }

    private val browseNetworkMutex = Mutex()
    private val searchNetworkMutex = Mutex()
    private val videoDetailMutex = Mutex()

    val likedItemIds: StateFlow<Set<String>> =
        unifiedDao.getLikedItemIds() // Uses new DAO
            .map { it.toSet() }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptySet()
            )

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

    suspend fun fetchBrowseList(
        key: BrowseCacheKey,
        forceNetwork: Boolean = false,
        cachePolicy: CachePolicy = CachePolicy.CACHE_FIRST
    ): Result<FetcherResult<HolodexVideoItem>> = withContext(defaultDispatcher) {
        Timber.d("$TAG: fetchBrowseList called. Key: ${key.stringKey()}, forceNetwork: $forceNetwork, policy: $cachePolicy")
        try {
            when (cachePolicy) {
                CachePolicy.CACHE_FIRST -> fetchBrowseWithCacheFirst(key, forceNetwork)
                CachePolicy.NETWORK_FIRST -> fetchBrowseWithNetworkFirst(key)
                CachePolicy.CACHE_ONLY -> fetchBrowseFromCacheOnly(key)
                CachePolicy.NETWORK_ONLY -> fetchBrowseFromNetworkOnly(key)
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Unhandled exception in fetchBrowseList for key ${key.stringKey()}")
            Result.failure(
                CacheException.StorageError(
                    "Failed to fetch browse list for key ${key.stringKey()}",
                    e
                )
            )
        }
    }

    private suspend fun fetchBrowseWithCacheFirst(
        key: BrowseCacheKey,
        forceNetwork: Boolean
    ): Result<FetcherResult<HolodexVideoItem>> {
        if (!forceNetwork) {
            browseListCache.get(key)?.let {
                Timber.d("$TAG: Browse CACHE_FIRST hit for key: ${key.stringKey()}")
                return Result.success(it)
            }
        }
        Timber.d("$TAG: Browse CACHE_FIRST miss or forceNetwork for key: ${key.stringKey()}. Fetching from network with fallback.")
        return fetchBrowseFromNetworkWithFallback(key)
    }

    private suspend fun fetchBrowseWithNetworkFirst(key: BrowseCacheKey): Result<FetcherResult<HolodexVideoItem>> {
        Timber.d("$TAG: Browse NETWORK_FIRST for key: ${key.stringKey()}. Fetching from network with fallback.")
        return fetchBrowseFromNetworkWithFallback(key)
    }

    private suspend fun fetchBrowseFromCacheOnly(key: BrowseCacheKey): Result<FetcherResult<HolodexVideoItem>> {
        return browseListCache.get(key)?.let {
            Timber.d("$TAG: Browse CACHE_ONLY hit for key: ${key.stringKey()}")
            Result.success(it)
        }
            ?: Result.failure(CacheException.NotFound("No cached browse data for key ${key.stringKey()}"))
    }

    private suspend fun fetchBrowseFromNetworkOnly(key: BrowseCacheKey): Result<FetcherResult<HolodexVideoItem>> {
        Timber.d("$TAG: Browse NETWORK_ONLY for key: ${key.stringKey()}. Fetching directly from network.")
        return fetchBrowseFromNetwork(key)
    }

    private suspend fun fetchBrowseFromNetworkWithFallback(key: BrowseCacheKey): Result<FetcherResult<HolodexVideoItem>> {
        val networkResult = fetchBrowseFromNetwork(key)
        if (networkResult.isSuccess) {
            return networkResult
        } else {
            val networkError = networkResult.exceptionOrNull() ?: CacheException.NetworkError(
                "Unknown browse network error for ${key.stringKey()}",
                null
            )
            Timber.w(
                networkError,
                "$TAG: Browse network fetch failed for ${key.stringKey()}. Trying stale cache."
            )
            browseListCache.getStale(key)?.let { staleData ->
                Timber.d("$TAG: Browse using STALE cache for ${key.stringKey()} after network failure.")
                return Result.success(staleData)
            } ?: return Result.failure(networkError)
        }
    }

    private suspend fun fetchBrowseFromNetwork(key: BrowseCacheKey): Result<FetcherResult<HolodexVideoItem>> {
        return browseNetworkMutex.withLock {
            try {
                val apiRequest = VideoSearchRequest(
                    sort = key.filters.sortField.apiValue,
                    target = listOf("stream", "clip"),
                    topic = key.filters.selectedPrimaryTopic?.let { listOf(it) } ?: DEFAULT_MUSIC_TOPICS,
                    org = key.filters.selectedOrganization?.let { listOf(it) },
                    paginated = true,
                    offset = key.pageOffset,
                    limit = DEFAULT_PAGE_SIZE
                )
                val response = holodexApiService.searchVideosAdvanced(apiRequest)
                if (!response.isSuccessful || response.body() == null) {
                    throw IOException("API Error")
                }

                val videosFromApi = response.body()!!.items

                // --- NEW SIMPLIFIED FILTERING LOGIC ---
                val filteredVideos = videosFromApi.filter { video ->
                    // Must be music content AND longer than 60 seconds
                    VideoFilteringUtil.isMusicContent(video) && video.duration > 60
                }
                // --------------------------------------

                val fetcherResult = FetcherResult(
                    filteredVideos,
                    response.body()?.getTotalAsInt(),
                    key.pageOffset + filteredVideos.size
                )
                browseListCache.store(key, fetcherResult)
                Result.success(fetcherResult)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }


    suspend fun fetchSearchList(
        key: SearchCacheKey,
        forceNetwork: Boolean = false,
        cachePolicy: CachePolicy = CachePolicy.CACHE_FIRST
    ): Result<FetcherResult<HolodexVideoItem>> = withContext(defaultDispatcher) {
        Timber.d("$TAG: fetchSearchList called. Key: ${key.stringKey()}, forceNetwork: $forceNetwork, policy: $cachePolicy")
        try {
            when (cachePolicy) {
                CachePolicy.CACHE_FIRST -> fetchSearchWithCacheFirst(key, forceNetwork)
                CachePolicy.NETWORK_FIRST -> fetchSearchWithNetworkFirst(key)
                CachePolicy.CACHE_ONLY -> fetchSearchFromCacheOnly(key)
                CachePolicy.NETWORK_ONLY -> fetchSearchFromNetworkOnly(key)
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Unhandled exception in fetchSearchList for key ${key.stringKey()}")
            Result.failure(
                CacheException.StorageError(
                    "Failed to fetch search list for key ${key.stringKey()}",
                    e
                )
            )
        }
    }

    private suspend fun fetchSearchWithCacheFirst(
        key: SearchCacheKey,
        forceNetwork: Boolean
    ): Result<FetcherResult<HolodexVideoItem>> {
        if (!forceNetwork) {
            searchListCache.get(key)?.let {
                Timber.d("$TAG: Search CACHE_FIRST hit for key: ${key.stringKey()}")
                return Result.success(it)
            }
        }
        Timber.d("$TAG: Search CACHE_FIRST miss or forceNetwork for key: ${key.stringKey()}. Fetching from network with fallback.")
        return fetchSearchFromNetworkWithFallback(key)
    }

    private suspend fun fetchSearchWithNetworkFirst(key: SearchCacheKey): Result<FetcherResult<HolodexVideoItem>> {
        Timber.d("$TAG: Search NETWORK_FIRST for key: ${key.stringKey()}. Fetching from network with fallback.")
        return fetchSearchFromNetworkWithFallback(key)
    }

    private suspend fun fetchSearchFromCacheOnly(key: SearchCacheKey): Result<FetcherResult<HolodexVideoItem>> {
        return searchListCache.get(key)?.let {
            Timber.d("$TAG: Search CACHE_ONLY hit for key: ${key.stringKey()}")
            Result.success(it)
        }
            ?: Result.failure(CacheException.NotFound("No cached search data for key ${key.stringKey()}"))
    }

    private suspend fun fetchSearchFromNetworkOnly(key: SearchCacheKey): Result<FetcherResult<HolodexVideoItem>> {
        Timber.d("$TAG: Search NETWORK_ONLY for key: ${key.stringKey()}. Fetching directly from network.")
        return fetchSearchFromNetwork(key)
    }

    private suspend fun fetchSearchFromNetworkWithFallback(key: SearchCacheKey): Result<FetcherResult<HolodexVideoItem>> {
        val networkResult = fetchSearchFromNetwork(key)
        if (networkResult.isSuccess) {
            return networkResult
        } else {
            val networkError = networkResult.exceptionOrNull() ?: CacheException.NetworkError(
                "Unknown search network error for ${key.stringKey()}",
                null
            )
            Timber.w(
                networkError,
                "$TAG: Search network fetch failed for ${key.stringKey()}. Trying stale cache."
            )
            searchListCache.getStale(key)?.let { staleData ->
                Timber.d("$TAG: Search using STALE cache for ${key.stringKey()} after network failure.")
                return Result.success(staleData)
            } ?: return Result.failure(networkError)
        }
    }

    fun getStarredPlaylistsFlow(): Flow<List<StarredPlaylistEntity>> {
        return starredPlaylistDao.getStarredPlaylists()
    }

    @UnstableApi
    private suspend fun fetchSearchFromNetwork(key: SearchCacheKey): Result<FetcherResult<HolodexVideoItem>> {
        return searchNetworkMutex.withLock {
            Timber.d("$TAG: Fetching SEARCH from network: Key=${key.stringKey()}")
            try {
                val actualTextSearchConditions: List<SearchCondition>?
                val actualChannelIdForVch: List<String>?

                if (key.query.startsWith(VideoListViewModel.CHANNEL_ID_SEARCH_PREFIX)) {
                    val actualChannelId =
                        key.query.removePrefix(VideoListViewModel.CHANNEL_ID_SEARCH_PREFIX)
                    actualChannelIdForVch =
                        if (actualChannelId.isNotBlank()) listOf(actualChannelId) else null
                    actualTextSearchConditions = null
                } else {
                    actualTextSearchConditions = listOf(SearchCondition(text = key.query))
                    actualChannelIdForVch = null
                }

                val apiRequest = VideoSearchRequest(
                    sort = "newest",
                    target = listOf("stream", "clip"),
                    conditions = actualTextSearchConditions,
                    topic = DEFAULT_MUSIC_TOPICS,
                    vch = actualChannelIdForVch,
                    paginated = true,
                    offset = key.pageOffset,
                    limit = DEFAULT_PAGE_SIZE
                )
                val response = holodexApiService.searchVideosAdvanced(apiRequest)
                if (!response.isSuccessful || response.body() == null) {
                    throw IOException("API Error (Search) for '${key.query}': ${response.code()} - ${response.message()}")
                }

                val videosFromApi = response.body()!!.items
                val musicallyRelevantVideos =
                    videosFromApi.filter { VideoFilteringUtil.isMusicContent(it) }
                Timber.d(
                    "$TAG: Search network fetch successful for ${key.stringKey()}. Items: ${musicallyRelevantVideos.size}, Total API: ${
                        response.body()?.getTotalAsInt()
                    }"
                )
                val fetcherResult = FetcherResult(
                    musicallyRelevantVideos,
                    response.body()?.getTotalAsInt(),
                    key.pageOffset + musicallyRelevantVideos.size
                )
                searchListCache.store(key, fetcherResult)
                Result.success(fetcherResult)
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "$TAG: Exception during search network fetch for key ${key.stringKey()}"
                )
                Result.failure(
                    CacheException.NetworkError(
                        "Search network fetch failed for ${key.stringKey()}",
                        e
                    )
                )
            }
        }
    }

    suspend fun getVideoWithSongs(
        videoId: String,
        forceRefresh: Boolean = false
    ): Result<HolodexVideoItem> = withContext(defaultDispatcher) {
        videoDetailMutex.withLock {
            if (!forceRefresh) {
                val cachedVideoWithSongs = videoDao.getVideoWithSongsOnce(videoId)
                if (cachedVideoWithSongs != null && System.currentTimeMillis() - cachedVideoWithSongs.video.fetchedAtMs < CACHE_STALE_DURATION_MS) {
                    Timber.d("$TAG: getVideoWithSongs (ID: $videoId) - Returning FRESH network-cached version from VideoDao.")
                    return@withLock Result.success(cachedVideoWithSongs.toDomain())
                }
            }

            Timber.d("$TAG: getVideoWithSongs (ID: $videoId) - No suitable cached version found. Fetching from NETWORK (forceRefresh=$forceRefresh).")
            try {
                val response = holodexApiService.getVideoWithSongs(
                    videoId = videoId,
                    include = "songs,live_info,description",
                    lang = "en"
                )

                if (response.isSuccessful && response.body() != null) {
                    val videoFromApi = response.body()!!
                    videoFromApi.songs?.forEach { it.videoId = videoFromApi.id }

                    val existingVideoEntity = videoDao.getVideoByIdOnce(videoId)
                    val entityToSave = videoFromApi.toEntity(
                        queryKey = existingVideoEntity?.listQueryKey,
                        insertionOrder = existingVideoEntity?.insertionOrder ?: 0,
                        currentTimestamp = System.currentTimeMillis()
                    )
                    val songEntitiesToSave =
                        videoFromApi.songs?.map { it.toEntity(videoFromApi.id) } ?: emptyList()

                    appDatabase.withTransaction {
                        videoDao.insertVideo(entityToSave)
                        videoDao.deleteSongsForVideo(videoFromApi.id)
                        if (songEntitiesToSave.isNotEmpty()) {
                            videoDao.insertSongs(songEntitiesToSave)
                        }
                    }
                    Result.success(videoFromApi)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown API error"
                    Timber.e("$TAG: API Error ${response.code()} for getVideoWithSongs ($videoId): $errorBody")
                    Result.failure(IOException("API Error ${response.code()} for $videoId: $errorBody"))
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Network Exception for getVideoWithSongs ($videoId)")
                val staleVideo = videoDao.getVideoWithSongsOnce(videoId)
                if (staleVideo != null) {
                    Timber.w("$TAG: Network failed, but returning STALE cached data for $videoId.")
                    Result.success(staleVideo.toDomain())
                } else {
                    Result.failure(
                        CacheException.NetworkError(
                            "Network fetch failed for $videoId and no cache is available.",
                            e
                        )
                    )
                }
            }
        }
    }

    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        playlistDao.getItemsForPlaylist(playlistId)

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    suspend fun getLastItemOrderInPlaylist(playlistId: Long): Int? =
        withContext(defaultDispatcher) { playlistDao.getLastItemOrder(playlistId) }

    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity? =
        withContext(defaultDispatcher) { playlistDao.getPlaylistById(playlistId) }


    suspend fun clearAllCachedData() = withContext(Dispatchers.IO) {
        Timber.i("$TAG: Clearing temporary application caches.")
        browseListCache.clear()
        searchListCache.clear()
        appDatabase.withTransaction {
            videoDao.clearAllSongs()
            videoDao.clearAllVideos()
        }
        Timber.i("$TAG: Temporary application caches cleared from repository.")
    }

    suspend fun cleanupExpiredCacheEntries() = withContext(Dispatchers.IO) {
        Timber.d("$TAG: Cleaning up expired cache entries.")
        browseListCache.cleanupExpiredEntries()
        searchListCache.cleanupExpiredEntries()
    }


    suspend fun getFavoritesFeed(
        channels: List<UnifiedDisplayItem>,
        filters: BrowseFilterState,
        offset: Int
    ): Result<FetcherResult<HolodexVideoItem>> = withContext(defaultDispatcher) {

        Timber.e("========== DEBUG: FAVORITES FEED INPUT ==========")
        Timber.e("Total Input Channels: ${channels.size}")
        channels.forEachIndexed { index, item ->
            // We log the Title and isExternal flag for every channel to identify the culprit
            Timber.e("[$index] ${item.title} | ID: ${item.playbackItemId} | isExternal: ${item.isExternal}")
        }
        Timber.e("=================================================")

        if (channels.isEmpty()) {
            return@withContext Result.success(FetcherResult(emptyList(), 0))
        }

        try {
            val holodexChannels = channels.filter { !it.isExternal }.map { it.playbackItemId }
            val externalChannels = channels.filter { it.isExternal }.map { it.playbackItemId }

            Timber.d("FavoritesFeed: Input -> Holodex IDs: ${holodexChannels.size}, External IDs: ${externalChannels.size}")

            // If Holodex IDs are 0, the Mapper 'isExternal' logic is still the root cause.

            val semaphore = kotlinx.coroutines.sync.Semaphore(10)

            // 1. Fetch Holodex
            val holodexResults = if (holodexChannels.isNotEmpty()) {
                coroutineScope {
                    holodexChannels.map { channelId ->
                        async {
                            semaphore.withPermit {
                                try {
                                    val request = VideoSearchRequest(
                                        sort = filters.sortField.apiValue,
                                        vch = listOf(channelId),
                                        topic = filters.selectedPrimaryTopic?.let { listOf(it) } ?: DEFAULT_MUSIC_TOPICS,
                                        paginated = true,
                                        offset = 0,
                                        limit = 15,
                                        target = listOf("stream", "clip")
                                    )
                                    val res = holodexApiService.searchVideosAdvanced(request).body()?.items ?: emptyList()
                                    // Debug log per channel
                                     Timber.v("Holodex Fetch $channelId: ${res.size} items")
                                    res
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to fetch Holodex favorites for $channelId")
                                    emptyList()
                                }
                            }
                        }
                    }.awaitAll().flatten()
                }
            } else emptyList()

            // 2. Fetch External
            val externalResults = if (externalChannels.isNotEmpty()) {
                // LIMIT CONCURRENCY FOR NEWPIPE TO 4
                // NewPipe uses heavy HTML parsing which can cause OOM/Native crashes if run too parallel
                val externalSemaphore = kotlinx.coroutines.sync.Semaphore(4)

                coroutineScope {
                    externalChannels.map { extId ->
                        async {
                            externalSemaphore.withPermit {
                                getMusicFromExternalChannel(extId, null).getOrNull()?.data ?: emptyList()
                            }
                        }
                    }.awaitAll().flatten()
                }
            } else emptyList()

            Timber.d("FavoritesFeed: Raw Results -> Holodex: ${holodexResults.size}, External: ${externalResults.size}")

            // 3. Merge & Filter
            val allVideos = holodexResults + externalResults

            // Filter duration > 60s
            val validVideos = allVideos.filter { it.duration > 60 }

            // 4. Robust Sorting
            // Parse the 'availableAt' string to Instant/Millis for correct comparison
            val sortedList = validVideos
                .distinctBy { it.id }
                .sortedByDescending { video ->
                    try {
                        // Handle ISO 8601 string
                        if (video.availableAt.isNotEmpty()) {
                            java.time.Instant.parse(video.availableAt).toEpochMilli()
                        } else 0L
                    } catch (e: Exception) {
                        0L // Fallback for bad dates
                    }
                }

            // 5. Paginate
            val paginatedList = if (sortedList.size > offset) {
                sortedList.drop(offset).take(DEFAULT_PAGE_SIZE)
            } else {
                emptyList()
            }

            Result.success(FetcherResult(paginatedList, totalAvailable = null))

        } catch (e: Exception) {
            Timber.e(e, "Error in getFavoritesFeed")
            Result.failure(e)
        }
    }

    suspend fun getUpcomingMusicPaginated(
        org: String?,
        offset: Int
    ): Result<FetcherResult<HolodexVideoItem>> {
        val filters = BrowseFilterState.create(
            preset = ViewTypePreset.UPCOMING_STREAMS,
            organization = org,
        )
        val key = BrowseCacheKey(filters, offset)
        return fetchBrowseList(key, forceNetwork = true)
    }

    suspend fun getDiscoveryHubContent(org: String): Result<DiscoveryResponse> =
        withContext(defaultDispatcher) {
            val cacheKey = "discovery_org_$org"
            try {
                val cachedResponse = discoveryDao.getResponse(cacheKey)
                if (cachedResponse != null) {
                    val isStale =
                        System.currentTimeMillis() - cachedResponse.timestamp > DISCOVERY_CACHE_TTL_MS
                    Timber.d("$TAG: Discovery Hub cache HIT for key '$cacheKey'. Is stale: $isStale")
                    if (!isStale) {
                        return@withContext Result.success(cachedResponse.data)
                    } else {
                        launch { fetchAndCacheDiscoveryContent(org) }
                        return@withContext Result.success(cachedResponse.data)
                    }
                }
                Timber.d("$TAG: Discovery Hub cache MISS for key '$cacheKey'. Fetching from network.")
                return@withContext fetchAndCacheDiscoveryContent(org)

            } catch (e: Exception) {
                Timber.e(e, "$TAG: Exception in getDiscoveryHubContent for org '$org'")
                Result.failure(e)
            }
        }

    suspend fun getFavoritesHubContent(): Result<DiscoveryResponse> =
        withContext(defaultDispatcher) {
            val cacheKey = "discovery_favorites"
            try {
                val cachedResponse = discoveryDao.getResponse(cacheKey)
                if (cachedResponse != null) {
                    val isStale =
                        System.currentTimeMillis() - cachedResponse.timestamp > DISCOVERY_CACHE_TTL_MS
                    Timber.d("$TAG: Favorites Hub cache HIT for key '$cacheKey'. Is stale: $isStale")
                    if (!isStale) {
                        return@withContext Result.success(cachedResponse.data)
                    } else {
                        launch { fetchAndCacheFavoritesContent() }
                        return@withContext Result.success(cachedResponse.data)
                    }
                }
                Timber.d("$TAG: Favorites Hub cache MISS for key '$cacheKey'. Fetching from network.")
                return@withContext fetchAndCacheFavoritesContent()

            } catch (e: Exception) {
                Timber.e(e, "$TAG: Exception in getFavoritesHubContent")
                Result.failure(e)
            }
        }

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

    @JvmName("getHotSongsForCarouselByChannelId")
    suspend fun getHotSongsForCarousel(channelId: String): Result<List<MusicdexSong>> =
        withContext(defaultDispatcher) {
            try {
                val response =
                    holodexApiService.getHotSongs(organization = null, channelId = channelId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(IOException("API Error fetching hot songs for channel '$channelId': ${response.code()}"))
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

    private suspend fun fetchAndCacheDiscoveryContent(org: String): Result<DiscoveryResponse> {
        return try {
            val response = musicdexApiService.getDiscoveryForOrg(org)
            if (response.isSuccessful && response.body() != null) {
                val discoveryResponse = response.body()!!
                val cacheEntry = CachedDiscoveryResponse(
                    pageKey = "discovery_org_$org",
                    data = discoveryResponse
                )
                discoveryDao.insertResponse(cacheEntry)
                Timber.i("$TAG: Successfully fetched and cached discovery content for org '$org'.")
                Result.success(discoveryResponse)
            } else {
                Result.failure(IOException("API Error fetching discovery content for org '$org': ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDiscoveryForChannel(channelId: String): Result<DiscoveryResponse> =
        withContext(defaultDispatcher) {
            try {
                val response = musicdexApiService.getDiscoveryForChannel(channelId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(IOException("API Error fetching channel discovery: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun fetchAndCacheFavoritesContent(): Result<DiscoveryResponse> {
        return try {
            val response = authenticatedMusicdexApiService.getDiscoveryForFavorites()
            if (response.isSuccessful && response.body() != null) {
                val discoveryResponse = response.body()!!
                val cacheEntry = CachedDiscoveryResponse(
                    pageKey = "discovery_favorites",
                    data = discoveryResponse
                )
                discoveryDao.insertResponse(cacheEntry)
                Timber.i("$TAG: Successfully fetched and cached favorites discovery content.")
                Result.success(discoveryResponse)
            } else {
                Result.failure(IOException("API Error fetching favorites discovery content: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestSongsPaginated(
        offset: Int,
        limit: Int = 25
    ): Result<PaginatedSongsResponse> {
        return try {
            val request = LatestSongsRequest(offset = offset, limit = limit, paginated = true)
            val response = holodexApiService.getLatestSongs(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("API Error fetching latest songs: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to fetch latest songs.")
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

    suspend fun getChannelDetails(channelId: String): Result<ChannelDetails> =
        withContext(defaultDispatcher) {
            try {
                val response = holodexApiService.getChannelDetails(channelId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(IOException("API Error fetching channel details: ${response.code()}"))
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
            }.awaitAll().filterNotNull() // Launch all lookups in parallel, wait for them, and filter out any failures
        }
    // --- END OF MODIFICATION ---

    suspend fun reconcileLocalPlaylistItems(localPlaylistId: Long, remoteSongs: List<MusicdexSong>) {
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
            Timber.tag(TAG_SYNC).i("Saved edits for playlist '${editedPlaylist.name}'. Marked as DIRTY for next sync.")
        } else {
            Timber.tag(TAG_SYNC).i("Saved local-only edits for playlist '${editedPlaylist.name}'. Sync status remains SYNCED.")
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


    suspend fun searchMusicOnChannels(
        query: String,
        channelIds: List<String>
    ): Result<List<HolodexVideoItem>> = withContext(defaultDispatcher) {
        if (channelIds.isEmpty()) return@withContext Result.success(emptyList())

        try {
            val ytService = NewPipe.getService(ServiceList.YouTube.serviceId)
            val allResults = coroutineScope {
                channelIds.map { channelId ->
                    async {
                        searchSingleChannel(ytService, channelId, query)
                    }
                }.awaitAll()
            }

            Result.success(allResults.flatten())
        } catch (e: Exception) {
            Timber.e(e, "Failed to perform external channel search")
            Result.failure(e)
        }
    }

    private suspend fun searchSingleChannel(
        ytService: StreamingService,
        channelId: String,
        query: String
    ): List<HolodexVideoItem> = try {
        val channelUrl = "https://www.youtube.com/channel/$channelId"

        val channelExtractor = ytService.getChannelExtractor(channelUrl)
        channelExtractor.fetchPage()

        val avatarUrl = channelExtractor.avatars.firstOrNull()?.url.orEmpty()
        val channelName = channelExtractor.name

        val videosTab = channelExtractor.tabs.find { tab ->
            tab.contentFilters.contains("videos")
        }

        if (videosTab != null) {
            val tabExtractor = ytService.getChannelTabExtractor(videosTab)
            tabExtractor.fetchPage()

            tabExtractor.initialPage.items
                .mapNotNull { it as? StreamInfoItem }
                .filter { it.name.contains(query, ignoreCase = true) }
                .map { item ->
                    mapStreamInfoItemToHolodexVideoItem(
                        item,
                        channelId,
                        channelName,
                        avatarUrl
                    )
                }
                .filter { VideoFilteringUtil.isMusicContent(it) }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to search within channel $channelId")
        emptyList()
    }



    suspend fun getMusicFromExternalChannel(
        channelId: String,
        nextPage: org.schabi.newpipe.extractor.Page?
    ): Result<FetcherResult<HolodexVideoItem>> = withContext(defaultDispatcher) {
        try {
            // ... (Keep existing NewPipe setup code) ...
            val ytService = NewPipe.getService(ServiceList.YouTube.serviceId)
            val channelUrl = "https://www.youtube.com/channel/$channelId"
            val channelExtractor = ytService.getChannelExtractor(channelUrl)
            channelExtractor.fetchPage()

            val videosTab = channelExtractor.tabs.firstOrNull { it.getUrl().contains("/videos", ignoreCase = true) }
                ?: return@withContext Result.failure(Exception("Could not find Videos tab"))

            val tabExtractor = ytService.getChannelTabExtractor(videosTab)
            val itemsPage = if (nextPage == null) {
                tabExtractor.fetchPage()
                tabExtractor.initialPage
            } else {
                tabExtractor.getPage(nextPage)
            }

            if (itemsPage == null || itemsPage.items.isEmpty()) {
                return@withContext Result.success(FetcherResult(emptyList(), null, null, null))
            }

            val videos = itemsPage.items.mapNotNull { it as? StreamInfoItem }
            val avatarUrl = channelExtractor.avatars.firstOrNull()?.url.orEmpty()

            val holodexItems = videos.map { item ->
                mapStreamInfoItemToHolodexVideoItem(item, channelId, channelExtractor.name, avatarUrl)
            }

            // --- NEW FILTERING LOGIC ---
            // Filter > 60s and Music Content
            val musicContent = holodexItems.filter {
                VideoFilteringUtil.isMusicContent(it) && it.duration > 60
            }
            // ---------------------------

            Result.success(
                FetcherResult(
                    data = musicContent,
                    totalAvailable = null,
                    nextPageCursor = if (itemsPage.hasNextPage()) itemsPage.nextPage else null
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    private fun mapStreamInfoItemToHolodexVideoItem(
        item: StreamInfoItem,
        channelId: String,
        channelName: String,
        channelAvatar: String
    ): HolodexVideoItem {
        val timestamp = try {
            item.uploadDate?.offsetDateTime()?.toInstant() ?: Instant.now()
        } catch (e: Exception) { Instant.now() }

        val videoId = try {
            item.url.substringAfter("watch?v=").substringBefore("&")
        } catch (e: Exception) { item.url }

        return HolodexVideoItem(
            id = videoId,
            title = item.name,
            type = "stream",
            topicId = null, // External items don't have Holodex topics
            availableAt = timestamp.toString(),
            publishedAt = timestamp.toString(),
            duration = item.duration.takeIf { it > 0 } ?: 0,
            status = "past", // Assume external videos are past
            channel = HolodexChannelMin(
                id = channelId,
                name = channelName,
                englishName = null,
                org = "External", // Mark as External
                type = "vtuber",
                photoUrl = channelAvatar
            ),
            songcount = 0, // External items don't have a song count from Holodex
            description = null, // StreamInfoItem doesn't have a full description, StreamExtractor does
            songs = null
        )
    }

    suspend fun searchForExternalChannels(query: String): Result<List<com.example.holodex.data.model.ChannelSearchResult>> = withContext(defaultDispatcher) {
        try {
            val ytService = NewPipe.getService(ServiceList.YouTube.serviceId)
            val extractor = ytService.getSearchExtractor(query, listOf("channels"), "")
            extractor.fetchPage()

            val results = extractor.initialPage.items.mapNotNull { it as? org.schabi.newpipe.extractor.channel.ChannelInfoItem }
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