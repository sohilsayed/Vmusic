// File: java/com/example/holodex/playback/domain/model/StreamDetails.kt
package com.example.holodex.playback.domain.model

data class StreamDetails(
    val url: String,
    val format: String?,
    val quality: String?
)