package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchPageDao {
    @Query("SELECT * FROM cached_search_pages WHERE pageKey = :pageKey")
    suspend fun getPage(pageKey: String): CachedSearchPage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: CachedSearchPage)

    @Query("DELETE FROM cached_search_pages WHERE pageKey = :pageKey")
    suspend fun deletePage(pageKey: String)

    /**
     * Deletes pages older than the given timestamp for search caches.
     */
    @Query("DELETE FROM cached_search_pages WHERE timestamp < :expiredTime")
    suspend fun deleteExpiredSearchPages(expiredTime: Long)

    /**
     * Deletes all pages from the search cache.
     */
    @Query("DELETE FROM cached_search_pages")
    suspend fun deleteAllSearchPages()

    @Query("SELECT COUNT(pageKey) FROM cached_search_pages")
    suspend fun getSearchCacheSize(): Int
}