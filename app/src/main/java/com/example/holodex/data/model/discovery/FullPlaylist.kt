// File: java/com/example/holodex/data/model/discovery/FullPlaylist.kt
package com.example.holodex.data.model.discovery

import com.google.gson.annotations.SerializedName

data class FullPlaylist(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("content") val content: List<MusicdexSong>?
)