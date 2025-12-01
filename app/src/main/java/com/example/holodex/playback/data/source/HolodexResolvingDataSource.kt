package com.example.holodex.playback.data.source

import android.net.Uri
import androidx.collection.LruCache
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.repository.YouTubeStreamRepository
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class HolodexResolvingDataSource @Inject constructor(
    private val streamRepository: YouTubeStreamRepository,
    private val unifiedDao: UnifiedDao
) : ResolvingDataSource.Resolver {

    private data class CachedUrl(val url: String, val timestamp: Long)
    private val urlCache = LruCache<String, CachedUrl>(20)
    private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(60)

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri

        if (uri.scheme == "holodex" && uri.host == "resolve") {
            val rawId = uri.lastPathSegment ?: return dataSpec

            // ----------------------------------------------------------------
            // 1. CHECK LOCAL DATABASE (PURE SYNC - NO COROUTINES)
            // ----------------------------------------------------------------
            val localPath = try {
                // A. Try exact match
                var interaction = unifiedDao.getDownloadInteractionSync(rawId)

                // B. If not found, try parent ID
                if (interaction == null && rawId.contains("_")) {
                    val parentId = rawId.substringBeforeLast("_")
                    interaction = unifiedDao.getDownloadInteractionSync(parentId)
                }

                if (interaction != null &&
                    interaction.downloadStatus == DownloadStatus.COMPLETED.name &&
                    !interaction.localFilePath.isNullOrBlank()
                ) {
                    val path = interaction.localFilePath!!
                    if (path.startsWith("content://") || File(path).exists()) {
                        path
                    } else {
                        Timber.w("ResolvingDataSource: DB path missing on disk: $path")
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking DB for local file (Sync)")
                null
            }

            if (localPath != null) {
                Timber.i("ResolvingDataSource: Using LOCAL file for $rawId")
                return dataSpec.buildUpon()
                    .setUri(Uri.parse(localPath))
                    .build()
            }

            // ----------------------------------------------------------------
            // 2. NETWORK RESOLUTION
            // ----------------------------------------------------------------
            val videoIdForNetwork = if (rawId.contains("_") && rawId.substringAfterLast("_").all { it.isDigit() }) {
                rawId.substringBeforeLast("_")
            } else {
                rawId
            }

            synchronized(urlCache) {
                val cachedEntry = urlCache[videoIdForNetwork]
                if (cachedEntry != null) {
                    if (System.currentTimeMillis() - cachedEntry.timestamp < CACHE_TTL_MS) {
                        return dataSpec.buildUpon().setUri(Uri.parse(cachedEntry.url)).build()
                    } else {
                        urlCache.remove(videoIdForNetwork)
                    }
                }
            }

            Timber.i("ResolvingDataSource: Resolving network for $videoIdForNetwork")

            // Network calls inherently require blocking or async.
            // Since we can't use async here easily without blocking (which causes the issue),
            // and runBlocking is what failed, we need a safe way.
            // Ideally, we shouldn't do network calls in resolveDataSpec if we can avoid it,
            // but NewPipe requires extraction.

            // We will try runBlocking ONLY for network, as it takes longer.
            // But if it fails/cancels, we return original spec to let ExoPlayer handle the error naturally.
            val streamDetails = try {
                runBlocking {
                    streamRepository.getAudioStreamDetails(videoIdForNetwork).getOrNull()
                }
            } catch (e: Exception) {
                Timber.w("Network resolution cancelled or failed for $videoIdForNetwork")
                null
            }

            if (streamDetails != null) {
                synchronized(urlCache) {
                    urlCache.put(videoIdForNetwork, CachedUrl(streamDetails.streamUrl, System.currentTimeMillis()))
                }
                return dataSpec.buildUpon().setUri(Uri.parse(streamDetails.streamUrl)).build()
            } else {
                // Don't throw exception here. Return the original Spec.
                // ExoPlayer will try to load "holodex://..." and fail with a standard HttpError or similar,
                // which might trigger a retry policy instead of a hard crash/skip.
                return dataSpec
            }
        }

        return dataSpec
    }
}