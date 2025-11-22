package com.example.holodex.data.model

import com.google.gson.annotations.SerializedName

data class VideoSearchRequest(
    @SerializedName("sort") val sort: String = "newest",
    @SerializedName("lang") val lang: List<String>? = null,
    @SerializedName("target") val target: List<String>, // e.g., ["stream", "clip"]
    @SerializedName("conditions") val conditions: List<SearchCondition>? = null, // For text search
    @SerializedName("topic") val topic: List<String>? = null, // For topic filtering
    @SerializedName("vch") val vch: List<String>? = null, // For channel ID search
    @SerializedName("org") val org: List<String>? = null, // For organization filtering
    @SerializedName("paginated") val paginated: Boolean = true,
    @SerializedName("offset") val offset: Int = 0,
    @SerializedName("limit") val limit: Int = 25,
    // @SerializedName("status") val status: List<String>? = null // REMOVE if /search/videoSearch POST body doesn't support it
    // 'comment' field was also in the original openapi spec example but not in your data class, add if needed
    @SerializedName("comment") val comment: List<String>? = null
)

data class SearchCondition(
    @SerializedName("text") val text: String
)