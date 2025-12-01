package com.example.holodex.playback.data.source

import androidx.collection.LruCache
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UserInteractionEntity
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
    private val unifiedDao: UnifiedDao // <-- REPLACED DownloadedItemDao
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

    fun getCachedUrl(videoId: String): String? {
        return getStreamFromCache(videoId)?.url
    }

    private suspend fun resolveSingleStreamInternal(
        item: PlaybackItem,
        prewarmedDownloads: Map<String, UserInteractionEntity>? // Type changed
    ): PlaybackItem? {
        if (!item.streamUri.isNullOrBlank()) {
            return item
        }

        // --- THE FIX ---
        // Check for download interaction in the unified table
        val downloadInteraction = prewarmedDownloads?.get(item.id) ?: unifiedDao.getDownloadInteraction(item.id)
        if (downloadInteraction != null &&
            downloadInteraction.downloadStatus == DownloadStatus.COMPLETED.name &&
            !downloadInteraction.localFilePath.isNullOrBlank()) {
            return item.copy(streamUri = downloadInteraction.localFilePath)
        }
        // --- END FIX ---

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