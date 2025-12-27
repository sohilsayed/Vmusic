package com.example.holodex.data.repository

import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.MemoryPolicy
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.impl.extensions.get
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

@Singleton
class StreamUrlRepository @Inject constructor(
    private val youTubeStreamRepository: YouTubeStreamRepository
) {
    // We build this manually because it's a simple Memory-Only store (No Database)
    private val store = StoreBuilder.from(
        fetcher = Fetcher.of { videoId: String ->
            // This is the expensive NewPipe call (2-5 seconds)
            val details = youTubeStreamRepository.getAudioStreamDetails(videoId).getOrThrow()
            details.streamUrl
        }
    )
        .cachePolicy(
            MemoryPolicy.builder<Any, Any>()
                .setExpireAfterWrite(90.minutes) // URLs usually expire after 6h, 4h is safe
                .setMaxSize(50) // Keep last 50 songs in RAM
                .build()
        )
        .build()

    /**
     * Returns the cached URL immediately if available.
     * If not, it fetches it (blocking).
     * Used by the ResolvingDataSource.
     */
    suspend fun getStreamUrl(videoId: String): String {
        return store.get(videoId)
    }
}