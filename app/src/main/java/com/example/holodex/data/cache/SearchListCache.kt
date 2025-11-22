package com.example.holodex.data.cache

import androidx.collection.LruCache
import com.example.holodex.data.db.CachedSearchPage
import com.example.holodex.data.db.SearchPageDao
import com.example.holodex.data.model.HolodexVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SearchListCache(
    private val searchPageDao: SearchPageDao,
    private val maxMemoryPages: Int = 3, // Search might be less frequently revisited per specific query
    private val cacheValidityMs: Long = TimeUnit.MINUTES.toMillis(30),
    private val staleValidityMs: Long = TimeUnit.HOURS.toMillis(12)
) {
    private data class CachePageEntry(
        val result: FetcherResult<HolodexVideoItem>,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val memoryCache = object : LruCache<String, CachePageEntry>(maxMemoryPages) {
        override fun sizeOf(key: String, value: CachePageEntry): Int = 1
    }

    private fun isFresh(timestamp: Long): Boolean = System.currentTimeMillis() - timestamp < cacheValidityMs
    private fun isStaleButAcceptable(timestamp: Long): Boolean = System.currentTimeMillis() - timestamp < staleValidityMs

    suspend fun get(key: SearchCacheKey): FetcherResult<HolodexVideoItem>? = withContext(Dispatchers.IO) {
        val stringKey = key.stringKey()
        memoryCache[stringKey]?.let { entry ->
            if (isFresh(entry.timestamp)) {
                Timber.d("SearchListCache: Memory HIT (fresh) for key: $stringKey")
                return@withContext entry.result
            } else {
                Timber.d("SearchListCache: Memory STALE for key: $stringKey, removing.")
                memoryCache.remove(stringKey)
            }
        }

        searchPageDao.getPage(stringKey)?.let { cachedPage ->
            if (isFresh(cachedPage.timestamp)) {
                Timber.d("SearchListCache: Disk HIT (fresh) for key: $stringKey")
                val result = FetcherResult(cachedPage.data, cachedPage.totalAvailable)
                memoryCache.put(stringKey, CachePageEntry(result, cachedPage.timestamp))
                return@withContext result
            }
        }
        Timber.d("SearchListCache: Cache MISS for key: $stringKey")
        null
    }

    suspend fun getStale(key: SearchCacheKey): FetcherResult<HolodexVideoItem>? = withContext(Dispatchers.IO) {
        val stringKey = key.stringKey()
        memoryCache[stringKey]?.let { entry ->
            if (isStaleButAcceptable(entry.timestamp)) {
                Timber.d("SearchListCache: Memory HIT (stale acceptable) for key: $stringKey")
                return@withContext entry.result
            }
        }

        searchPageDao.getPage(stringKey)?.let { cachedPage ->
            if (isStaleButAcceptable(cachedPage.timestamp)) {
                Timber.d("SearchListCache: Disk HIT (stale acceptable) for key: $stringKey")
                return@withContext FetcherResult(cachedPage.data, cachedPage.totalAvailable)
            } else {
                Timber.d("SearchListCache: Disk EXPIRED (too stale) for key: $stringKey, deleting.")
                searchPageDao.deletePage(stringKey)
            }
        }
        Timber.d("SearchListCache: Stale cache MISS for key: $stringKey")
        null
    }

    suspend fun store(key: SearchCacheKey, result: FetcherResult<HolodexVideoItem>) = withContext(Dispatchers.IO) {
        val stringKey = key.stringKey()
        val currentTimestamp = System.currentTimeMillis()
        val entry = CachePageEntry(result, currentTimestamp)
        memoryCache.put(stringKey, entry)

        val cachedPage = CachedSearchPage(
            pageKey = stringKey,
            data = result.data,
            totalAvailable = result.totalAvailable,
            timestamp = currentTimestamp
        )
        searchPageDao.insertPage(cachedPage)
        Timber.d("SearchListCache: Stored data for key: $stringKey, items: ${result.data.size}")
    }

    suspend fun invalidate(key: SearchCacheKey) = withContext(Dispatchers.IO) {
        val stringKey = key.stringKey()
        memoryCache.remove(stringKey)
        searchPageDao.deletePage(stringKey)
        Timber.d("SearchListCache: Invalidated data for key: $stringKey")
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        searchPageDao.deleteAllSearchPages()
        Timber.d("SearchListCache: Cleared all search cache data.")
    }

    suspend fun cleanupExpiredEntries() = withContext(Dispatchers.IO) {
        val tooOldTimestamp = System.currentTimeMillis() - staleValidityMs
        searchPageDao.deleteExpiredSearchPages(tooOldTimestamp)
        Timber.d("SearchListCache: Cleaned up expired disk entries older than $staleValidityMs ms.")
    }
}