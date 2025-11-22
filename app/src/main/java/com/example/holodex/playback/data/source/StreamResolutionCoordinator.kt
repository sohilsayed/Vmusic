// File: java/com/example/holodex/playback/data/source/StreamResolutionCoordinator.kt
package com.example.holodex.playback.data.source

import androidx.collection.LruCache
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.DownloadedItemDao
import com.example.holodex.data.db.DownloadedItemEntity
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.model.StreamDetails
import com.example.holodex.playback.domain.repository.StreamResolverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

@UnstableApi
class StreamResolutionCoordinator(
    private val streamResolverRepository: StreamResolverRepository,
    private val downloadedItemDao: DownloadedItemDao
) {
    companion object {
        private const val TAG = "StreamResolutionCoord"
    }

    private var currentResolutionJob: Job? = null
    private data class ResolvedStreamCacheEntry(val streamDetails: StreamDetails, val timestamp: Long)
    private val streamUrlCache = LruCache<String, ResolvedStreamCacheEntry>(50)
    private val cacheExpiryMs = TimeUnit.MINUTES.toMillis(10)

    suspend fun resolveSingleStream(item: PlaybackItem): PlaybackItem? {
        return withContext(Dispatchers.IO) {
            resolveSingleStreamInternal(item.copy(), null)
        }
    }

    fun clearMemoryCache() {
        streamUrlCache.evictAll()
    }

    private suspend fun resolveSingleStreamInternal(
        item: PlaybackItem,
        prewarmedDownloads: Map<String, DownloadedItemEntity>?
    ): PlaybackItem? {
        if (!item.streamUri.isNullOrBlank()) {
            return item
        }

        val downloadedItem = prewarmedDownloads?.get(item.id) ?: downloadedItemDao.getById(item.id)
        if (downloadedItem != null && downloadedItem.downloadStatus == DownloadStatus.COMPLETED && !downloadedItem.localFileUri.isNullOrBlank()) {
            return item.copy(streamUri = downloadedItem.localFileUri)
        }

        getStreamFromCache(item.videoId)?.let { cachedDetails ->
            return item.copy(streamUri = cachedDetails.url)
        }

        if (!coroutineContext.isActive) {
            return null
        }

        return try {
            val result = streamResolverRepository.resolveStreamUrl(item.videoId)
            if (result.isSuccess) {
                val streamDetails = result.getOrThrow()
                putStreamInCache(item.videoId, streamDetails)
                item.copy(streamUri = streamDetails.url)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getStreamFromCache(videoId: String): StreamDetails? {
        val entry = streamUrlCache[videoId]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < cacheExpiryMs) {
                return entry.streamDetails
            } else {
                streamUrlCache.remove(videoId)
            }
        }
        return null
    }

    private fun putStreamInCache(videoId: String, streamDetails: StreamDetails) {
        streamUrlCache.put(videoId, ResolvedStreamCacheEntry(streamDetails, System.currentTimeMillis()))
    }

    fun cancelOngoingResolutions() {
        synchronized(this) {
            currentResolutionJob?.cancel()
            currentResolutionJob = null
        }
    }
}