// File: java/com/example/holodex/data/cache/FetcherResult.kt
package com.example.holodex.data.cache

data class FetcherResult<V>(
    val data: List<V>,
    val totalAvailable: Int?,
    val nextPageOffset: Int? = null,
    val nextPageCursor: Any? = null // Generic cursor for NewPipe's Page object
)