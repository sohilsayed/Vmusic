package com.example.holodex.data.repository

import com.example.holodex.data.api.HolodexApiService
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.mappers.toFlattenedMetadataEntities
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.MemoryPolicy
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class VideoRepository @Inject constructor(
    private val api: HolodexApiService,
    private val unifiedDao: UnifiedDao
) {
    private val store = StoreBuilder.from(
        fetcher = Fetcher.of { videoId: String ->
            val response = api.getVideoWithSongs(videoId)
            if (!response.isSuccessful) throw Exception("API Error ${response.code()}")
            response.body()!!
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = { videoId: String ->
                unifiedDao.getItemByIdFlow(videoId).map { it?.toUnifiedDisplayItem() }
            },
            writer = { _: String, videoItem ->
                // Flatten the video and its songs into a list of entities
                val allEntities = videoItem.toFlattenedMetadataEntities()
                unifiedDao.upsertMetadataList(allEntities)
            }
        )
    )
        // FIX: Use cachePolicy for MemoryPolicy, not validator
        .cachePolicy(
            MemoryPolicy.builder<Any, Any>()
                .setExpireAfterWrite(4.hours)
                .build()
        )
        .build()

    fun getVideo(videoId: String, refresh: Boolean = false): Flow<StoreReadResponse<UnifiedDisplayItem>> {
        return store.stream(StoreReadRequest.cached(key = videoId, refresh = refresh))
    }
    fun getVideoSegments(videoId: String): Flow<List<UnifiedDisplayItem>> {
        return unifiedDao.getSegmentsForVideo(videoId).map { list ->
            list.map { it.toUnifiedDisplayItem() }
        }
    }
    /**
     * Helper for use-cases that need a single value instead of a stream.
     * Waits for the first Data emission.
     */
    suspend fun getVideoSnapshot(videoId: String): UnifiedDisplayItem? {
        return try {
            // FIX: Use .milliseconds for Duration
            kotlinx.coroutines.withTimeoutOrNull(5_000.milliseconds) {
                getVideo(videoId)
                    .filter { it is StoreReadResponse.Data }
                    .map { it.dataOrNull() }
                    .first()
            }
        } catch (e: Exception) {
            null
        }
    }

    // Helper to get Segments Snapshot
    suspend fun getVideoSegmentsSnapshot(videoId: String): List<UnifiedDisplayItem> {
        return getVideoSegments(videoId).first()
    }
}