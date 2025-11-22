// File: java/com/example/holodex/data/model/ChannelSearchResult.kt
package com.example.holodex.data.model

data class ChannelSearchResult(
    val channelId: String,
    val name: String,
    val thumbnailUrl: String?,
    val subscriberCount: String?
)