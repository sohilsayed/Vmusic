package com.example.holodex.data.repository

import com.example.holodex.data.api.HolodexApiService
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.mappers.toUnifiedMetadataEntity
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.SearchCondition
import com.example.holodex.data.model.VideoSearchRequest
import com.example.holodex.data.store.UnifiedStoreFactory
import com.example.holodex.util.VideoFilteringUtil
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.state.BrowseFilterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class FeedKey(
    val org: String?,
    val sort: String,
    val topic: String?,
    val channelId: String?,
    val query: String? = null,
    val offset: Int,
    val type: String = "stream",
    val status: String? = null
)

@Singleton
class FeedRepository @Inject constructor(
    private val api: HolodexApiService,
    private val unifiedDao: UnifiedDao,
    storeFactory: UnifiedStoreFactory
) {

    private val DEFAULT_MUSIC_TOPICS = listOf(
        "singing", "Music_Cover", "Original_Song", "music", "mv",
        "karaoke", "3D_Stream", "concert"
    )

    private val store = storeFactory.createListStore(
        fetcher = { key: FeedKey ->
            if (key.org == "Favorites") {
                fetchFavoritesFeed(key)
            } else if (key.query != null) {
                fetchSearchResults(key)
            } else {
                fetchStandardFeed(key)
            }
        },
        networkToEntity = { it },
        modelToId = { it.id }
    )

    private val hotSongsStore = storeFactory.createListStore(
        fetcher = { channelId: String ->
            val idParam = if (channelId == "all") null else channelId
            val isOrg = channelId != "all" && !channelId.startsWith("UC")

            val response = if (isOrg) {
                api.getHotSongs(organization = channelId)
            } else {
                api.getHotSongs(channelId = idParam)
            }

            if (!response.isSuccessful) throw Exception("Hot Songs API Error ${response.code()}")

            val songs = response.body() ?: emptyList()
            songs.map { it.toUnifiedMetadataEntity() }
        },
        networkToEntity = { it },
        modelToId = { it.id }
    )

    // ============================================================================================
    // PUBLIC API - WITH LIVE HYDRATION
    // ============================================================================================

    fun getFeed(
        filter: BrowseFilterState,
        offset: Int,
        refresh: Boolean = false,
        channelId: String? = null,
        searchQuery: String? = null
    ): Flow<StoreReadResponse<List<UnifiedDisplayItem>>> {

        val key = FeedKey(
            org = filter.selectedOrganization?.takeIf { it != "All Vtubers" },
            sort = filter.sortField.apiValue,
            topic = filter.selectedPrimaryTopic,
            channelId = channelId,
            query = searchQuery,
            offset = offset,
            status = filter.status
        )

        // Combine the Store Flow (Static Data) with DB Flows (Live Status)
        return combine(
            store.stream(StoreReadRequest.cached(key, refresh)),
            unifiedDao.getLikedItemIds(), // Live Flow of Liked IDs
            unifiedDao.getDownloadsFeed() // Live Flow of Downloads (to check status)
        ) { response, likedIds, downloads ->

            if (response is StoreReadResponse.Data) {
                val likedSet = likedIds.toSet()
                val downloadMap = downloads.associate { it.metadata.id to it }

                val hydratedList = response.value.map { item ->
                    // Re-calculate status based on live DB data
                    val isLiked = likedSet.contains(item.playbackItemId)
                    val downloadEntry = downloadMap[item.playbackItemId]
                    val isDownloaded = downloadEntry != null && downloadEntry.interactions.any { it.downloadStatus == "COMPLETED" }
                    val status = downloadEntry?.interactions?.find { it.interactionType == "DOWNLOAD" }?.downloadStatus

                    // Only copy if something changed to avoid recomposition spam
                    if (item.isLiked != isLiked || item.isDownloaded != isDownloaded || item.downloadStatus != status) {
                        item.copy(
                            isLiked = isLiked,
                            isDownloaded = isDownloaded,
                            downloadStatus = status,
                            localFilePath = downloadEntry?.interactions?.find { it.interactionType == "DOWNLOAD" }?.localFilePath
                        )
                    } else {
                        item
                    }
                }
                StoreReadResponse.Data(hydratedList, response.origin)
            } else {
                response // Loading or Error, pass through
            }
        }
    }

    fun getHotSongs(
        channelId: String,
        refresh: Boolean = false
    ): Flow<StoreReadResponse<List<UnifiedDisplayItem>>> {
        // Apply same hydration logic to Hot Songs
        return combine(
            hotSongsStore.stream(StoreReadRequest.cached(channelId, refresh)),
            unifiedDao.getLikedItemIds(),
            unifiedDao.getDownloadsFeed()
        ) { response, likedIds, downloads ->
            if (response is StoreReadResponse.Data) {
                val likedSet = likedIds.toSet()
                val downloadMap = downloads.associate { it.metadata.id to it }

                val hydrated = response.value.map { item ->
                    val isLiked = likedSet.contains(item.playbackItemId)
                    val downloadEntry = downloadMap[item.playbackItemId]
                    item.copy(
                        isLiked = isLiked,
                        isDownloaded = downloadEntry != null,
                        localFilePath = downloadEntry?.interactions?.find { it.interactionType == "DOWNLOAD" }?.localFilePath
                    )
                }
                StoreReadResponse.Data(hydrated, response.origin)
            } else {
                response
            }
        }
    }

    // ... (Fetcher Implementations remain unchanged) ...
    // ... (copy fetchStandardFeed, fetchSearchResults, fetchFavoritesFeed, mappers from previous file) ...
    // Note: Ensure the private methods below are kept exactly as they were in the previous version.

    private suspend fun fetchStandardFeed(key: FeedKey): List<UnifiedMetadataEntity> {
        val safeSort = if (key.sort == "oldest") "oldest" else "newest"
        val topicsToQuery = if (key.topic != null) listOf(key.topic) else DEFAULT_MUSIC_TOPICS

        val request = VideoSearchRequest(
            sort = safeSort,
            org = key.org?.let { listOf(it) },
            topic = topicsToQuery,
            vch = key.channelId?.let { listOf(it) },
            target = listOf(key.type),
            paginated = true,
            offset = key.offset,
            limit = 50
        )

        val response = api.searchVideosAdvanced(request)
        if (!response.isSuccessful) throw Exception("Feed API Error: ${response.code()}")

        return response.body()?.items?.asSequence()
            ?.filter { VideoFilteringUtil.isMusicContent(it) }
            ?.filter { item ->
                when (key.status) {
                    "past" -> item.status != "upcoming" && item.status != "live"
                    "upcoming" -> item.status == "upcoming" || item.status == "live"
                    else -> true
                }
            }
            ?.map { it.toUnifiedMetadataEntity() }
            ?.toList() ?: emptyList()
    }

    private suspend fun fetchSearchResults(key: FeedKey): List<UnifiedMetadataEntity> {
        val request = VideoSearchRequest(
            sort = "newest",
            conditions = listOf(SearchCondition(key.query!!)),
            paginated = true,
            offset = key.offset,
            limit = 50,
            target = listOf("stream", "clip")
        )
        val response = api.searchVideosAdvanced(request)
        if (!response.isSuccessful) throw Exception("Search API Error: ${response.code()}")

        return response.body()?.items
            ?.filter { VideoFilteringUtil.isMusicContent(it) }
            ?.map { it.toUnifiedMetadataEntity() }
            ?: emptyList()
    }

    private suspend fun fetchFavoritesFeed(key: FeedKey): List<UnifiedMetadataEntity> =
        coroutineScope {
            val channels = unifiedDao.getFavoriteChannelsSync()
            val holodexChannels = channels.filter { it.metadata.org != "External" }.map { it.metadata.id }
            val externalChannels = channels.filter { it.metadata.org == "External" }.map { it.metadata.id }

            val holodexSemaphore = Semaphore(10)
            val externalSemaphore = Semaphore(4)

            val holodexResults = holodexChannels.map { channelId ->
                async {
                    holodexSemaphore.withPermit {
                        try {
                            val request = VideoSearchRequest(
                                sort = "newest",
                                vch = listOf(channelId),
                                topic = if (key.topic != null) listOf(key.topic) else DEFAULT_MUSIC_TOPICS,
                                paginated = true,
                                offset = 0,
                                limit = 10,
                                target = listOf("stream", "clip")
                            )
                            api.searchVideosAdvanced(request).body()?.items
                                ?.filter { VideoFilteringUtil.isMusicContent(it) }
                                ?.map { it.toUnifiedMetadataEntity() }
                                ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
            }

            val externalResults = externalChannels.map { channelId ->
                async(Dispatchers.IO) {
                    externalSemaphore.withPermit {
                        scrapeExternalChannel(channelId)
                    }
                }
            }

            val allItems = (holodexResults.awaitAll().flatten() + externalResults.awaitAll().flatten())
            val sorted = allItems
                .distinctBy { it.id }
                .filter { it.duration > 60 }
                .sortedByDescending { it.availableAt ?: it.publishedAt ?: "0" }

            if (sorted.size > key.offset) {
                sorted.drop(key.offset).take(50)
            } else {
                emptyList()
            }
        }

    private fun scrapeExternalChannel(channelId: String): List<UnifiedMetadataEntity> {
        return try {
            val ytService = NewPipe.getService(ServiceList.YouTube.serviceId)
            val channelExtractor = ytService.getChannelExtractor("https://www.youtube.com/channel/$channelId")
            channelExtractor.fetchPage()

            val videosTab = channelExtractor.tabs.firstOrNull { it.url.contains("videos") } ?: return emptyList()
            val tabExtractor = ytService.getChannelTabExtractor(videosTab)
            tabExtractor.fetchPage()

            val channelName = channelExtractor.name
            val avatarUrl = channelExtractor.avatars.firstOrNull()?.url.orEmpty()

            tabExtractor.initialPage.items
                .mapNotNull { it as? StreamInfoItem }
                .map { item -> mapStreamInfoItemToHolodexVideoItem(item, channelId, channelName, avatarUrl) }
                .filter { VideoFilteringUtil.isMusicContent(it) && it.duration > 60 }
                .map { it.toUnifiedMetadataEntity(overrideType = "VIDEO") }
        } catch (e: Exception) {
            emptyList()
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
            topicId = null,
            availableAt = timestamp.toString(),
            publishedAt = timestamp.toString(),
            duration = item.duration.takeIf { it > 0 } ?: 0,
            status = "past",
            channel = com.example.holodex.data.model.HolodexChannelMin(
                id = channelId,
                name = channelName,
                englishName = null,
                org = "External",
                type = "vtuber",
                photoUrl = channelAvatar
            ),
            songcount = 0,
            description = null,
            songs = null
        )
    }

    private fun com.example.holodex.data.model.discovery.MusicdexSong.toUnifiedMetadataEntity(): UnifiedMetadataEntity {
        return UnifiedMetadataEntity(
            id = "${this.videoId}_${this.start}",
            title = this.name,
            artistName = this.channel.name,
            type = "SEGMENT",
            specificArtUrl = this.artUrl,
            uploaderAvatarUrl = this.channel.photoUrl,
            duration = (this.end - this.start).toLong(),
            channelId = this.channel.id ?: "unknown",
            parentVideoId = this.videoId,
            startSeconds = this.start.toLong(),
            endSeconds = this.end.toLong(),
            lastUpdatedAt = System.currentTimeMillis()
        )
    }
}