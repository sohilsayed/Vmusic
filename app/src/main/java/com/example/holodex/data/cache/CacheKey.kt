package com.example.holodex.data.cache

import com.example.holodex.viewmodel.state.BrowseFilterState
import com.google.gson.Gson

/**
 * Base interface for cache keys to ensure a string representation for Room.
 */
interface CacheKey {
    fun stringKey(): String
}

data class BrowseCacheKey(
    val filters: BrowseFilterState,
    val pageOffset: Int
) : CacheKey {
    override fun stringKey(): String {
        val gson = Gson()
        val filterJson = gson.toJson(mapOf(
            "preset" to filters.selectedViewPreset.name,
            "org" to filters.selectedOrganization,
            "topic" to filters.selectedPrimaryTopic,
            "sortField" to filters.sortField.apiValue,
            "sortOrder" to filters.sortOrder.apiValue,
            "status" to filters.status,
            "maxUpcomingHours" to filters.maxUpcomingHours
        ))
        return "browse_${filterJson}_offset=$pageOffset"
    }
}

data class SearchCacheKey(
    val query: String,
    val pageOffset: Int
) : CacheKey {
    override fun stringKey(): String {
        return "search_query=${query.trim().replace(" ", "_").take(100)}_offset=$pageOffset"
    }
}

// Add other keys as needed, e.g., for Favorites, LikedSegments, PlaylistItems if they use similar paged caching
// data class FavoritesCacheKey(val itemType: LikedItemType, val pageOffset: Int) : CacheKey { ... }