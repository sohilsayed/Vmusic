// File: java/com/example/holodex/data/source/CacheSchemeDataSourceFactory.kt

package com.example.holodex.data.source

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec // <-- ADD THIS IMPORT
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import timber.log.Timber

/**
 * A custom DataSource.Factory that routes requests based on the URI scheme.
 * - Handles "http" and "https" by delegating to a standard HttpDataSource.
 * - Handles a custom "cache" scheme by delegating to a CacheDataSource,
 *   using the URI's authority as the cache key.
 */
@UnstableApi
class CacheSchemeDataSourceFactory(
    private val downloadCache: SimpleCache,
    private val upstreamFactory: DefaultHttpDataSource.Factory
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val defaultCacheDataSource = CacheDataSource(
            downloadCache,
            upstreamFactory.createDataSource(),
            CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
        )

        val cacheOnlyDataSource = CacheDataSource(downloadCache, null)

        return object : DataSource by defaultCacheDataSource {
            // The method signature is now corrected to match the DataSource interface.
            // It takes a single `DataSpec` parameter.
            override fun open(dataSpec: DataSpec): Long {
                return if (dataSpec.uri.scheme == "cache") {
                    Timber.d("CacheScheme: Routing 'cache://' URI to cache-only source. Key: ${dataSpec.uri.authority}")
                    // The logic inside remains correct. We create a new DataSpec using the authority as the key.
                    val newSpec = dataSpec.buildUpon().setKey(dataSpec.uri.authority).build()
                    cacheOnlyDataSource.open(newSpec)
                } else {
                    // For all other schemes (http, https), use the default CacheDataSource.
                    Timber.d("CacheScheme: Routing '${dataSpec.uri.scheme}' URI to default cache/network source.")
                    defaultCacheDataSource.open(dataSpec)
                }
            }
        }
    }
}