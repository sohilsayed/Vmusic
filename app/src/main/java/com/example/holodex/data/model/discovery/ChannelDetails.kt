package com.example.holodex.data.model.discovery

import com.google.gson.annotations.SerializedName

/**
 * Represents the full details of a channel from the /channels/{id} endpoint.
 */
data class ChannelDetails(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("english_name") val englishName: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("photo") val photoUrl: String?,
    @SerializedName("banner") val bannerUrl: String?,
    @SerializedName("org") val org: String?,
    @SerializedName("suborg") val suborg: String?,
    @SerializedName("twitter") val twitter: String?,
    @SerializedName("group") val group: String? // Add the correct field for grouping
)