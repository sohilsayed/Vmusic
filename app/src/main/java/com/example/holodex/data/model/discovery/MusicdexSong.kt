package com.example.holodex.data.model.discovery

import com.google.gson.annotations.SerializedName

data class MusicdexSong(
    @SerializedName("id") val id: String?,
    @SerializedName("song_id") val songId: String,
    @SerializedName("name") val name: String,
    @SerializedName("original_artist") val originalArtist: String?,
    @SerializedName("art") val artUrl: String?,
    @SerializedName("video_id") val videoId: String,
    @SerializedName("start") val start: Int,
    @SerializedName("end") val end: Int,
    @SerializedName("available_at") val available_at: String?,

    @SerializedName("channel_id") val channelId: String?,
    @SerializedName("channel") val channel: MusicdexChannel,
    @SerializedName("ts") val ts: String? = null
)

data class MusicdexChannel(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String,
    @SerializedName("english_name") val englishName: String?,
    @SerializedName("photo") val photoUrl: String?,
    @SerializedName("suborg") val suborg: String?
)