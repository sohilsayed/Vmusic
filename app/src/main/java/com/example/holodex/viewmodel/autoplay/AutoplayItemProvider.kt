package com.example.holodex.viewmodel.autoplay

import com.example.holodex.data.repository.VideoRepository
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import timber.log.Timber
import javax.inject.Inject

class AutoplayItemProvider @Inject constructor(
    private val videoRepository: VideoRepository
) {
    companion object {
        private const val TAG = "AutoplayItemProvider"
    }

    suspend fun provideNextItemsForAutoplay(
        currentScreenItems: List<UnifiedDisplayItem>,
        lastPlayedItemIdInQueue: String?
    ): List<PlaybackItem>? {

        if (currentScreenItems.isEmpty()) return null

        val nextCandidateResult = findNextCandidate(currentScreenItems, lastPlayedItemIdInQueue)

        if (nextCandidateResult is AutoplaySearchResult.Found) {
            val candidateItem = nextCandidateResult.item
            Timber.i("$TAG: Found next autoplay candidate: '${candidateItem.title}'")

            // 1. Check if the next item is a video container that needs expanding into segments
            if (shouldCheckForSegments(candidateItem)) {
                try {
                    // Fetch segments from repository (returns List directly)
                    val segments = videoRepository.getVideoSegmentsSnapshot(candidateItem.videoId)

                    if (segments.isNotEmpty()) {
                        Timber.i("$TAG: Expanding video '${candidateItem.title}' into ${segments.size} segments.")
                        return segments.map { it.toPlaybackItem() }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Failed to fetch segments for ${candidateItem.title}")
                }
            }

            // 2. Fallback: Just play the single item found
            val item = candidateItem.toPlaybackItem()
            if (validateAutoplayItem(item, lastPlayedItemIdInQueue)) {
                return listOf(item)
            }
        }

        return null
    }

    private fun findNextCandidate(
        currentScreenItems: List<UnifiedDisplayItem>,
        lastPlayedItemIdInQueue: String?
    ): AutoplaySearchResult {
        if (lastPlayedItemIdInQueue == null) {
            return if (currentScreenItems.isNotEmpty()) {
                AutoplaySearchResult.Found(currentScreenItems.first())
            } else {
                AutoplaySearchResult.NotFound("screen_list_empty")
            }
        }

        // Logic to find index of current item and return next
        // Matches by Video ID to handle segments belonging to same video
        val lastVideoId = if (lastPlayedItemIdInQueue.contains("_")) {
            lastPlayedItemIdInQueue.substringBeforeLast("_")
        } else {
            lastPlayedItemIdInQueue
        }

        val index = currentScreenItems.indexOfFirst { it.videoId == lastVideoId }
        if (index != -1 && index + 1 < currentScreenItems.size) {
            return AutoplaySearchResult.Found(currentScreenItems[index + 1])
        }

        return AutoplaySearchResult.NotFound("end_of_list")
    }

    private fun shouldCheckForSegments(item: UnifiedDisplayItem): Boolean {
        // Only check if it's NOT already a segment, and has a song count > 0
        return !item.isSegment && (item.songCount ?: 0) > 0
    }

    private fun validateAutoplayItem(item: PlaybackItem, lastId: String?): Boolean {
        return item.id != lastId
    }
}

// Definition for the result class
internal sealed class AutoplaySearchResult {
    data class Found(val item: UnifiedDisplayItem) : AutoplaySearchResult()
    data class NotFound(val reason: String) : AutoplaySearchResult()
}