// File: java/com/example/holodex/data/api/PlaylistRequestDtos.kt (NEW FILE)
package com.example.holodex.data.api

import com.google.gson.annotations.SerializedName

/**
 * A dedicated Data Transfer Object (DTO) for creating or updating a playlist via the API.
 * This class ONLY contains fields the server expects, solving the 500 error caused by
 * sending extra client-side fields like `is_deleted`.
 */
data class PlaylistUpdateRequest(
    @SerializedName("id")
    val id: String?, // Null when creating, non-null when updating

    @SerializedName("owner")
    val owner: Long,

    @SerializedName("title")
    val title: String?,

    @SerializedName("description")
    val description: String?,

    @SerializedName("type")
    val type: String = "ugp",

    /**
     * The complete list of song UUIDs. When sent, this will OVERWRITE the existing content.
     */
    @SerializedName("content")
    val content: List<String>
)