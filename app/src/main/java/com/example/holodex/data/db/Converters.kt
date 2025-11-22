// File: java/com/example/holodex/data/db/Converters.kt
package com.example.holodex.data.db

import androidx.room.TypeConverter
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.discovery.DiscoveryResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HolodexVideoItemListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromVideoItemList(value: List<HolodexVideoItem>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toVideoItemList(value: String?): List<HolodexVideoItem>? {
        return value?.let {
            try {
                val listType = object : TypeToken<List<HolodexVideoItem>>() {}.type
                gson.fromJson(it, listType)
            } catch (e: Exception) {
                // Handle potential GSON parsing errors, e.g., if JSON is malformed
                null // Or throw, or log
            }
        }
    }
}

// Adding the StringListConverter here as well for co-location
class StringListConverter {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.joinToString(separator = "|||")
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.split("|||")?.filter { it.isNotEmpty() }
    }
}
class DiscoveryResponseConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromDiscoveryResponse(value: DiscoveryResponse?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toDiscoveryResponse(value: String?): DiscoveryResponse? {
        return value?.let {
            try {
                gson.fromJson(it, DiscoveryResponse::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
