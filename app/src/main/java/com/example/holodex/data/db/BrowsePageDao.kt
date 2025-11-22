package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BrowsePageDao {
    @Query("SELECT * FROM cached_browse_pages WHERE pageKey = :pageKey")
    suspend fun getPage(pageKey: String): CachedBrowsePage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: CachedBrowsePage)

    @Query("DELETE FROM cached_browse_pages WHERE pageKey = :pageKey")
    suspend fun deletePage(pageKey: String)

    /**
     * Deletes pages older than the given timestamp for browse caches.
     * The pageKey for browse pages might start with a common prefix like "browse_".
     */
    @Query("DELETE FROM cached_browse_pages WHERE timestamp < :expiredTime")
    suspend fun deleteExpiredBrowsePages(expiredTime: Long)

    /**
     * Deletes all pages from the browse cache.
     */
    @Query("DELETE FROM cached_browse_pages")
    suspend fun deleteAllBrowsePages()

    // Optional: Method to get cache size for monitoring
    @Query("SELECT COUNT(pageKey) FROM cached_browse_pages")
    suspend fun getBrowseCacheSize(): Int
}