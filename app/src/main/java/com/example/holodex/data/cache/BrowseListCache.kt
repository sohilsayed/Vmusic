package com.example.holodex.data.cache

import androidx.collection.LruCache
import com.example.holodex.data.db.BrowsePageDao
import com.example.holodex.data.db.CachedBrowsePage
import com.example.holodex.data.model.HolodexVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class BrowseListCache(
    private val browsePageDao: BrowsePageDao,
    private val maxMemoryPages: Int = 5, // Max number of pages to keep in memory
    private val cacheValidityMs: Long = TimeUnit.HOURS.toMillis(1), // How long a cache entry is considered "fresh"
    private val staleValidityMs: Long = TimeUnit.DAYS.toMillis(1) // How long stale data can be used if network fails
) {
    private data class CachePageEntry(
        val result: FetcherResult<HolodexVideoItem>, // Store HolodexVideoItem directly
        val timestamp: Long = System.currentTimeMillis()
    )

    // LruCache stores pages by their string key.
    private val memoryCache = object : LruCache<String, CachePageEntry>(maxMemoryPages) {
        override fun sizeOf(key: String, value: CachePageEntry): Int {
            // For LruCache, size is often 1 per entry if maxMemoryPages is the primary constraint.
            // If it were based on actual memory footprint, it'd be more complex.
            return 1 // Each entry counts as 1 towards maxMemoryPages
        }
    }

    private fun isFresh(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp < cacheValidityMs
    }

    private fun isStaleButAcceptable(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp < staleValidityMs
    }

    suspend fun get(key: BrowseCacheKey): FetcherResult<HolodexVideoItem>? = withContext(Dispatchers.IO) {
        val stringKey = key.stringKey()
        memoryCache[stringKey]?.let { entry ->
            if (isFresh(entry.timestamp)) {
                Timber.d("BrowseListCache: Memory HIT (fresh) for key: $stringKey")
                return@withContext entry.result
            } else {
                Timber.d("BrowseListCache: Memory STALE for key: $stringKey, removing.")
                memoryCache.remove(stringKey) // Remove stale entry from memory
            }
        }

        // Check disk cache
        browsePageDao.getPage(stringKey)?.let { cachedPage ->
            if (isFresh(cachedPage.timestamp)) {
                Timber.d("BrowseListCache: Disk HIT (fresh) for key: $stringKey")
                val result = FetcherResult(cachedPage.data, cachedPage.totalAvailable)
                memoryCache.put(stringKey, CachePageEntry(result, cachedPage.timestamp)) // Populate memory
                return@withContext result
            } else {
                // Data on disk is stale (older than cacheValidityMs) but not necessarily expired from disk yet.
                // We won't use it as "fresh" here. If network fails, getStale might pick it up.
                Timber.d("BrowseListCache: Disk STALE for key: $stringKey. Will rely on network or getStale().")
                // Optionally, one could delete it here if it's also older than staleValidityMs,
                // but cleanupExpired is a more global approach.
            }
        }
        Timber.d("BrowseListCache: Cache MISS for key: $stringKey")
        null
    }

    suspend fun getStale(key: BrowseCacheKey): FetcherResult<HolodexVideoItem>? = withContext(Dispatchers.IO) {
        val stringKey = key.stringKey()
        // Try memory cache first (even if stale but acceptable)
        memoryCache[stringKey]?.let { entry ->
            if (isStaleButAcceptable(entry.timestamp)) {
                Timber.d("BrowseListCache: Memory HIT (stale but acceptable) for key: $stringKey")
                return@withContext entry.result
            }
        }

        // Try disk cache (stale but acceptable)
        browsePageDao.getPage(stringKey)?.let { cachedPage ->
            if (isStaleButAcceptable(cachedPage.timestamp)) {
                Timber.d("BrowseListCache: Disk HIT (stale but acceptable) for key: $stringKey")
                // Note: Not re-populating memory cache with stale data from disk here,
                // as 'get' should be the one populating with fresh data.
                return@withContext FetcherResult(cachedPage.data, cachedPage.totalAvailable)
            } else {
                // Stale data on disk is too old even for fallback, remove it.
                Timber.d("BrowseListCache: Disk EXPIRED (too stale) for key: $stringKey, deleting.")
                browsePageDao.deletePage(stringKey)
            }
        }
        Timber.d("BrowseListCache: Stale cache MISS for key: $stringKey")
        null
    }

    suspend fun store(key: BrowseCacheKey, result: FetcherResult<HolodexVideoItem>) = withContext(Dispatchers.IO) {
        val stringKey = key.stringKey()
        val currentTimestamp = System.currentTimeMillis()
        val entry = CachePageEntry(result, currentTimestamp)
        memoryCache.put(stringKey, entry)

        val cachedPage = CachedBrowsePage(
            pageKey = stringKey,
            data = result.data,
            totalAvailable = result.totalAvailable,
            timestamp = currentTimestamp
        )
        browsePageDao.insertPage(cachedPage)
        Timber.d("BrowseListCache: Stored data for key: $stringKey, items: ${result.data.size}")
    }

    suspend fun invalidate(key: BrowseCacheKey) = withContext(Dispatchers.IO) {
        val stringKey = key.stringKey()
        memoryCache.remove(stringKey)
        browsePageDao.deletePage(stringKey)
        Timber.d("BrowseListCache: Invalidated data for key: $stringKey")
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        browsePageDao.deleteAllBrowsePages()
        Timber.d("BrowseListCache: Cleared all browse cache data (memory and disk).")
    }

    suspend fun cleanupExpiredEntries() = withContext(Dispatchers.IO) {
        val tooOldTimestamp = System.currentTimeMillis() - staleValidityMs
        browsePageDao.deleteExpiredBrowsePages(tooOldTimestamp)
        // Memory cache entries are evicted by LRU or when found stale during 'get'.
        // Could also iterate memoryCache.snapshot().keys and remove based on timestamp if strict cleanup is needed.
        Timber.d("BrowseListCache: Cleaned up expired disk entries older than $staleValidityMs ms.")
    }
}