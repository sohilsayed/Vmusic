package com.example.holodex.playback.data.source

import android.net.Uri
import androidx.collection.LruCache
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.example.holodex.data.repository.YouTubeStreamRepository
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intercepts "holodex://" URIs.
 * Resolves the actual audio stream URL (m4a/webm) just-in-time.
 * Includes an in-memory cache to prevent re-resolving during seeking/buffering.
 */
@UnstableApi
@Singleton
class HolodexResolvingDataSource @Inject constructor(
    private val streamRepository: YouTubeStreamRepository
) : ResolvingDataSource.Resolver {

    // Simple data class to hold the URL and when it was fetched
    private data class CachedUrl(val url: String, val timestamp: Long)

    // Cache up to 20 recent URLs.
    // YouTube URLs typically expire after ~6 hours, but we use a shorter TTL to be safe.
    private val urlCache = LruCache<String, CachedUrl>(20)
    private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(60) // 1 Hour

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri

        // We only intercept our custom scheme
        if (uri.scheme == "holodex" && uri.host == "resolve") {
            val videoId = uri.lastPathSegment ?: return dataSpec

            // 1. Check Memory Cache
            synchronized(urlCache) {
                val cachedEntry = urlCache[videoId]
                if (cachedEntry != null) {
                    val age = System.currentTimeMillis() - cachedEntry.timestamp
                    if (age < CACHE_TTL_MS) {
                        Timber.d("ResolvingDataSource: Cache HIT for $videoId (Age: ${age / 1000}s)")
                        return dataSpec.buildUpon()
                            .setUri(Uri.parse(cachedEntry.url))
                            .build()
                    } else {
                        Timber.d("ResolvingDataSource: Cache EXPIRED for $videoId")
                        urlCache.remove(videoId)
                    }
                }
            }

            Timber.d("ResolvingDataSource: Cache MISS. Resolving network for $videoId")

            // 2. Network Fetch (Synchronous via runBlocking, safe on playback thread)
            val streamDetails = try {
                runBlocking {
                    streamRepository.getAudioStreamDetails(videoId).getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve stream for $videoId")
                null
            }

            if (streamDetails != null) {
                // 3. Store in Cache
                synchronized(urlCache) {
                    urlCache.put(
                        videoId,
                        CachedUrl(streamDetails.streamUrl, System.currentTimeMillis())
                    )
                }

                Timber.i("ResolvingDataSource: Resolved & Cached $videoId -> ${streamDetails.streamUrl}")
                return dataSpec.buildUpon()
                    .setUri(Uri.parse(streamDetails.streamUrl))
                    .build()
            } else {
                Timber.e("ResolvingDataSource: Failed to resolve URL for $videoId")
                throw java.io.IOException("Could not resolve stream for video $videoId")
            }
        }

        // Pass through local files and standard HTTP urls
        return dataSpec
    }
}