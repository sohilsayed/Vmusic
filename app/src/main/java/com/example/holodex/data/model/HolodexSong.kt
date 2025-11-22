package com.example.holodex.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class HolodexSong(
    @SerializedName("name") val name: String,
    @SerializedName("start") val start: Int,
    @SerializedName("end") val end: Int,
    @SerializedName("itunesid") val itunesId: Int?,
    @SerializedName("art") val artUrl: String? = null,
    @SerializedName("original_artist") val originalArtist: String? = null,
    var videoId: String? = null
) : Parcelable