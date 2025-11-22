package com.example.holodex.data.model

import com.google.gson.annotations.SerializedName

data class HolodexVideoItem(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("type") val type: String,
    @SerializedName("topic_id") val topicId: String?,
    @SerializedName("available_at") val availableAt: String,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("duration") val duration: Long,
    @SerializedName("status") val status: String,
    @SerializedName("channel") val channel: HolodexChannelMin,
    @SerializedName("songcount") val songcount: Int?,
    @SerializedName("description") val description: String?,

    // This field will be populated when fetching a single video with "include=songs"
    @SerializedName("songs") val songs: List<HolodexSong>? = null
)

// HolodexChannelMin remains the same
data class HolodexChannelMin(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String,
    @SerializedName("english_name") val englishName: String?,
    @SerializedName("org") var org: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("photo") val photoUrl: String?
)