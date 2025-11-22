package com.example.holodex.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.example.holodex.data.model.HolodexVideoItem // Ensure this import is correct
import com.example.holodex.data.model.discovery.DiscoveryResponse

/**
 * Entity to store a "page" of browsed video items.
 * The pageKey should uniquely identify the filter set and offset/page number.
 */
@Entity(
    tableName = "cached_browse_pages",
    primaryKeys = ["pageKey"],
    indices = [Index(value = ["timestamp"])]
)
data class CachedBrowsePage(
    val pageKey: String, // Example: "browse_preset=LATEST_STREAMS_org=Hololive_topic=singing_sort=available_at_order=desc_offset=0"
    @ColumnInfo(name = "data_list")
    val data: List<HolodexVideoItem>, // Will use TypeConverter
    val timestamp: Long = System.currentTimeMillis(),
    val totalAvailable: Int? = null // Total items API reported for this query, to help determine endOfList
)

/**
 * Entity to store a "page" of searched video items.
 * The pageKey should uniquely identify the search query and offset/page number.
 */
@Entity(
    tableName = "cached_search_pages",
    primaryKeys = ["pageKey"],
    indices = [Index(value = ["timestamp"])]
)
data class CachedSearchPage(
    val pageKey: String, // Example: "search_query=my_search_term_offset=0"
    @ColumnInfo(name = "data_list")
    val data: List<HolodexVideoItem>, // Will use TypeConverter
    val timestamp: Long = System.currentTimeMillis(),
    val totalAvailable: Int? = null
)

@Entity(
    tableName = "cached_discovery_responses",
    primaryKeys = ["pageKey"],
    indices = [Index(value = ["timestamp"])]
)
data class CachedDiscoveryResponse(
    val pageKey: String, // e.g., "discovery_org_Hololive", "discovery_favorites"
    @ColumnInfo(name = "data_response")
    val data: DiscoveryResponse, // Will use TypeConverter
    val timestamp: Long = System.currentTimeMillis()
)
