package com.example.holodex.data.repository

import com.example.holodex.data.model.ChannelSearchResult
import com.example.holodex.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Responsible for all interactions with non-Holodex sources (NewPipe/YouTube Scraping).
 */
@Singleton
class ExternalContentRepository @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {

    suspend fun searchForExternalChannels(query: String): Result<List<ChannelSearchResult>> =
        withContext(defaultDispatcher) {
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
                Timber.e(e, "Failed to search for external channels with query: $query")
                Result.failure(e)
            }
        }
}