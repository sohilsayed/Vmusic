package com.example.holodex.data.model.discovery

import com.google.gson.annotations.SerializedName

/**
 * Represents a playlist "stub" as returned in discovery carousels.
 * It contains metadata but not the full list of songs.
 */
data class PlaylistStub(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("type") val type: String, // e.g., "ugp", "radio/artist"
    @SerializedName("art_context") val artContext: ArtContext?,
    @SerializedName("description") val description: String?
)

data class ArtContext(
    @SerializedName("videos") val videos: List<String>?, // Changed name and type
    @SerializedName("channels") val channels: List<String>?,
    @SerializedName("channel_photo") val channelPhotoUrl: String?
)