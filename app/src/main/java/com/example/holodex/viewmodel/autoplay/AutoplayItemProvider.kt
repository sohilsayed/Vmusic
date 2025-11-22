package com.example.holodex.viewmodel.autoplay

import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import timber.log.Timber

internal sealed class AutoplaySearchResult {
    data class Found(val item: UnifiedDisplayItem) : AutoplaySearchResult()
    data class NotFound(val reason: String) : AutoplaySearchResult()
}

class AutoplayItemProvider(
    private val holodexRepository: HolodexRepository
) {
    companion object {
        private const val TAG = "AutoplayItemProvider"
    }

    suspend fun provideNextItemsForAutoplay(
        currentScreenItems: List<UnifiedDisplayItem>,
        lastPlayedItemIdInQueue: String?,
        unifiedDisplayItemToPlaybackItem: (UnifiedDisplayItem) -> PlaybackItem,
        holodexSongToPlaybackItem: (HolodexVideoItem, HolodexSong) -> PlaybackItem
    ): List<PlaybackItem>? {
        if (currentScreenItems.isEmpty()) {
            Timber.w("$TAG: Current screen list is empty.")
            return null
        }

        try {
            val nextCandidateResult = findNextCandidate(currentScreenItems, lastPlayedItemIdInQueue)

            if (nextCandidateResult is AutoplaySearchResult.Found) {
                val candidateItem = nextCandidateResult.item
                Timber.i("$TAG: Found next autoplay candidate: '${candidateItem.title}'")

                if (shouldCheckForSegments(candidateItem)) {
                    val videoWithSongsResult = holodexRepository.getVideoWithSongs(candidateItem.videoId, forceRefresh = true)
                    if (videoWithSongsResult.isSuccess) {
                        val videoWithSongs = videoWithSongsResult.getOrThrow()
                        if (!videoWithSongs.songs.isNullOrEmpty()) {
                            Timber.i("$TAG: Video '${candidateItem.title}' has ${videoWithSongs.songs.size} segments. Preparing all for autoplay.")
                            return videoWithSongs.songs.map { song ->
                                holodexSongToPlaybackItem(videoWithSongs, song)
                            }
                        }
                    }
                }

                // If not checking for segments or if it fails, play the single item
                val singlePlaybackItem = unifiedDisplayItemToPlaybackItem(candidateItem)
                if (validateAutoplayItem(singlePlaybackItem, lastPlayedItemIdInQueue)) {
                    return listOf(singlePlaybackItem)
                }
                return null // Loop prevented
            } else {
                val reason = (nextCandidateResult as AutoplaySearchResult.NotFound).reason
                Timber.i("$TAG: No next autoplay candidate found. Reason: $reason.")
                return null
            }

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Exception during autoplay provider execution.")
            return null
        }
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

        val lastUnderscoreIndex = lastPlayedItemIdInQueue.lastIndexOf('_')

        // Check if there's an underscore and if the part after it is a number (the start time).
        val lastPartIsNumeric = lastUnderscoreIndex != -1 &&
                lastPlayedItemIdInQueue.substring(lastUnderscoreIndex + 1).all { it.isDigit() }

        val lastPlayedVideoId = if (lastPartIsNumeric) {
            // It's a song segment ID, so take the part before the last underscore.
            lastPlayedItemIdInQueue.substring(0, lastUnderscoreIndex)
        } else {
            // It's just a video ID, so use the whole string.
            lastPlayedItemIdInQueue
        }

        val indexOfLastPlayed = currentScreenItems.indexOfFirst { it.videoId == lastPlayedVideoId }

        if (indexOfLastPlayed != -1 && indexOfLastPlayed + 1 < currentScreenItems.size) {
            return AutoplaySearchResult.Found(currentScreenItems[indexOfLastPlayed + 1])
        }

        return AutoplaySearchResult.NotFound("end_of_list_reached")
    }


    private fun shouldCheckForSegments(item: UnifiedDisplayItem): Boolean {
        return !item.isSegment && (item.songCount ?: 0) > 0
    }

    private fun validateAutoplayItem(
        itemToPlay: PlaybackItem?,
        lastPlayedItemIdInQueue: String?
    ): Boolean {
        if (itemToPlay == null) return false
        if (lastPlayedItemIdInQueue == null) return true
        val notLooping = itemToPlay.id != lastPlayedItemIdInQueue
        if (!notLooping) {
            Timber.w("$TAG: Loop prevention! Attempted to autoplay same item ID: ${itemToPlay.id}")
        }
        return notLooping
    }
}