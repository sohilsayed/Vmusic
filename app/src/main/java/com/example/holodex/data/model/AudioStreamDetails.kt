package com.example.holodex.data.model // Make sure this package name matches your structure

import com.google.gson.annotations.SerializedName

// Represents what Musicdex or a similar service might return
data class AudioStreamDetails(
    @SerializedName("url") val streamUrl: String, // Direct audio stream URL
    @SerializedName("format") val format: String?, // e.g., "m4a", "opus"
    @SerializedName("quality") val quality: String? // e.g., "128kbps"
)