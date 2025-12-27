package com.example.holodex.data.repository

import com.example.holodex.data.api.HolodexApiService
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.mappers.toUnifiedMetadataEntity
import com.example.holodex.data.model.ChannelSearchResult
import com.example.holodex.data.model.HolodexChannelMin
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.store.UnifiedStoreFactory
import com.example.holodex.di.DefaultDispatcher
import com.example.holodex.playback.util.formatDurationSeconds
import com.example.holodex.util.IdUtil
import com.example.holodex.util.VideoFilteringUtil
import com.example.holodex.viewmodel.UnifiedDisplayItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Key to determine source
data class ChannelKey(val id: String, val isExternal: Boolean)

@Singleton
class ChannelRepository @Inject constructor(
    private val api: HolodexApiService,
    private val unifiedDao: UnifiedDao,
    storeFactory: UnifiedStoreFactory,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {

    private fun mapToEntity(details: ChannelDetails): UnifiedMetadataEntity {
        return UnifiedMetadataEntity(
            id = details.id,
            title = details.name,
            artistName = details.org ?: "Independents",
            type = "CHANNEL",
            specificArtUrl = details.photoUrl,
            uploaderAvatarUrl = details.photoUrl,
            duration = 0,
            channelId = details.id,
            description = details.description,
            org = details.org,
            lastUpdatedAt = System.currentTimeMillis()
        )
    }

    private val store = storeFactory.createItemStore(
        fetcher = { key: ChannelKey ->
            if (key.isExternal) {
                fetchExternalChannelDetails(key.id)
            } else {
                val response = api.getChannelDetails(key.id)
                if (!response.isSuccessful) throw Exception("API Error ${response.code()}")
                response.body()!!
            }
        },
        networkToEntity = { mapToEntity(it) },
        keyToId = { it.id }
    )

    fun getChannel(channelId: String, isExternal: Boolean): Flow<StoreReadResponse<UnifiedDisplayItem>> {
        return store.stream(StoreReadRequest.cached(key = ChannelKey(channelId, isExternal), refresh = false))
    }

    suspend fun searchForExternalChannels(query: String): Result<List<ChannelSearchResult>> = withContext(defaultDispatcher) {
        try {
            val ytService = NewPipe.getService(ServiceList.YouTube.serviceId)
            val extractor = ytService.getSearchExtractor(query, listOf("channels"), "")
            extractor.fetchPage()

            val results = extractor.initialPage.items
                .mapNotNull { it as? ChannelInfoItem }
                .map { infoItem ->
                    ChannelSearchResult(
                        channelId = infoItem.url.substringAfter("/channel/"),
                        name = infoItem.name,
                        thumbnailUrl = infoItem.thumbnails.firstOrNull()?.url,
                        subscriberCount = if (infoItem.subscriberCount > 0) "${infoItem.subscriberCount} subscribers" else null
                    )
                }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getExternalChannelVideos(channelId: String): List<UnifiedDisplayItem> = withContext(defaultDispatcher) {
        try {
            val ytService = NewPipe.getService(ServiceList.YouTube.serviceId)
            val channelExtractor = ytService.getChannelExtractor("https://www.youtube.com/channel/$channelId")
            channelExtractor.fetchPage()

            val videosTab = channelExtractor.tabs.firstOrNull { it.url.contains("videos") }
                ?: return@withContext emptyList()

            val tabExtractor = ytService.getChannelTabExtractor(videosTab)
            tabExtractor.fetchPage()

            val channelName = channelExtractor.name
            val avatarUrl = channelExtractor.avatars.firstOrNull()?.url.orEmpty()

            val videoItems = tabExtractor.initialPage.items
                .mapNotNull { it as? StreamInfoItem }
                .map { item ->
                    mapStreamInfoItemToHolodexVideoItem(item, channelId, channelName, avatarUrl)
                }
                .filter {
                    VideoFilteringUtil.isMusicContent(it) && it.duration > 60
                }

            // Save to DB
            val entities = videoItems.map { it.toUnifiedMetadataEntity(overrideType = "VIDEO") }
            unifiedDao.insertMetadataBatch(entities)

            // Map to UI Model
            videoItems.map { video ->
                UnifiedDisplayItem(
                    stableId = video.id,
                    playbackItemId = video.id,
                    navigationVideoId = IdUtil.extractVideoId(video.id), // FIX: Add this line
                    videoId = video.id,
                    channelId = channelId,
                    title = video.title,
                    artistText = channelName,
                    artworkUrls = listOfNotNull(video.channel.photoUrl),
                    durationText = formatDurationSeconds(video.duration),
                    isSegment = false,
                    songCount = 0,
                    isDownloaded = false,
                    downloadStatus = null,
                    localFilePath = null,
                    isLiked = false,
                    itemTypeForPlaylist = LikedItemType.VIDEO,
                    songStartSec = null,
                    songEndSec = null,
                    originalArtist = null,
                    isExternal = true
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchExternalChannelDetails(channelId: String): ChannelDetails {
        val ytService = NewPipe.getService(ServiceList.YouTube.serviceId)
        val extractor = ytService.getChannelExtractor("https://www.youtube.com/channel/$channelId")
        extractor.fetchPage()

        return ChannelDetails(
            id = channelId,
            name = extractor.name,
            englishName = extractor.name,
            description = extractor.description,
            photoUrl = extractor.avatars.firstOrNull()?.url,
            bannerUrl = extractor.banners.firstOrNull()?.url,
            org = "External",
            suborg = null,
            twitter = null,
            group = null
        )
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
            channel = HolodexChannelMin(
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
}