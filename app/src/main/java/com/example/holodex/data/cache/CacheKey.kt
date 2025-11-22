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
    val pageOffset: Int // Using offset as part of the key for paged data
) : CacheKey {
    override fun stringKey(): String {
        // A more stable serialization than default toString() for complex objects in keys.
        // Using Gson to serialize parts of the filter state.
        // Ensure BrowseFilterState and its members are GSON-serializable or use specific fields.
        val gson = Gson()
        val filterJson = gson.toJson(mapOf(
            "preset" to filters.selectedViewPreset.name,
            "org" to filters.selectedOrganization,
            "topic" to filters.selectedPrimaryTopic,
            "sortField" to filters.sortField.apiValue,
            "sortOrder" to filters.sortOrder.apiValue,
            "songSegmentFilter" to filters.songSegmentFilterMode.name,
            "status" to filters.status, // from selectedViewPreset
            "maxUpcomingHours" to filters.maxUpcomingHours // from selectedViewPreset
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