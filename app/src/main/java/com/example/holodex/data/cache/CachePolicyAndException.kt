package com.example.holodex.data.cache

enum class CachePolicy {
    /**
     * Try to fetch from cache first.
     * If cache miss or expired, fetch from network.
     * If network fails, attempt to use stale cache.
     */
    CACHE_FIRST,

    /**
     * Try to fetch from network first.
     * If network fails, attempt to use cache (fresh or stale).
     */
    NETWORK_FIRST,

    /**
     * Only fetch from cache. Fails if not found or expired.
     * Might have a sub-policy to allow stale.
     */
    CACHE_ONLY,

    /**
     * Only fetch from network. Do not use cache.
     */
    NETWORK_ONLY
}

sealed class CacheException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotFound(message: String) : CacheException(message)
    class Expired(message: String) : CacheException(message)
    class StorageError(message: String, cause: Throwable) : CacheException(message, cause)
    class NetworkError(message: String, cause: Throwable?) : CacheException(message, cause)
}