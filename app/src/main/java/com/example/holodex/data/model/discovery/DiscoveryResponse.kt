package com.example.holodex.data.model.discovery

import com.example.holodex.data.model.HolodexVideoItem
import com.google.gson.annotations.SerializedName


//Represents the entire aggregated response from the /musicdex/discovery/ endpoints.

data class DiscoveryResponse(
    @SerializedName("recentSingingStreams") val recentSingingStreams: List<SingingStreamShelfItem>?,
    @SerializedName("channels") val channels: List<DiscoveryChannel>?,
    @SerializedName("recommended") val recommended: RecommendedPlaylists?
)

data class SingingStreamShelfItem(
    @SerializedName("video") val video: HolodexVideoItem,
    // The playlist object here is a full playlist with content
    @SerializedName("playlist") val playlist: FullPlaylist
)

data class DiscoveryChannel(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("english_name") val englishName: String?,
    @SerializedName("photo") val photoUrl: String?,
    @SerializedName("song_count") val songCount: Int?,
    @SerializedName("suborg") val suborg: String? // Add the missing property
)

data class RecommendedPlaylists(
    @SerializedName("playlists") val playlists: List<PlaylistStub>
)