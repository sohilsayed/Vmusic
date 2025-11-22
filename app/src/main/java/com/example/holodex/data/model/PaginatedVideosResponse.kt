package com.example.holodex.data.model

import com.google.gson.annotations.SerializedName

data class PaginatedVideosResponse(
    @SerializedName("total") val total: String?, // API spec says number, but examples sometimes show string. String is safer.
    @SerializedName("items") val items: List<HolodexVideoItem>
) {
    // Convenience getter for total as Int
    fun getTotalAsInt(): Int? {
        return total?.toIntOrNull()
    }
}