// File: java/com/example/holodex/data/api/PlaylistDto.kt (NEW FILE)
package com.example.holodex.data.api

import com.google.gson.annotations.SerializedName

/**
 * A dedicated Data Transfer Object (DTO) for deserializing playlist data from the Holodex API.
 * The property names here EXACTLY match the JSON fields from the server.
 */
data class PlaylistDto(
    @SerializedName("id")
    val id: String,

    @SerializedName("title")
    val title: String?,

    @SerializedName("description")
    val description: String?,

    @SerializedName("owner")
    val owner: Long?,

    @SerializedName("type")
    val type: String?,

    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("updated_at")
    val updatedAt: String?
)