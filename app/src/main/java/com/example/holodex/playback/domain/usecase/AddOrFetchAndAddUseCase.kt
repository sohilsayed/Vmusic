// File: java/com/example/holodex/playback/domain/usecase/AddOrFetchAndAddUseCase.kt
package com.example.holodex.playback.domain.usecase

import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import timber.log.Timber
import javax.inject.Inject

/**
 * A use case that intelligently adds items to the playback queue.
 * - If the item is a song segment, it's added directly.
 * - If the item is a full video, it fetches the video's segments and adds all of them.
 * - If a video has no segments, it adds the full video itself.
 * @return A Result containing a user-friendly message for a Toast/Snackbar.
 */
class AddOrFetchAndAddUseCase @Inject constructor(
    private val holodexRepository: HolodexRepository,
    private val addItemsToQueueUseCase: AddItemsToQueueUseCase
) {
    suspend operator fun invoke(item: PlaybackItem): Result<String> {
        return try {
            if (item.songId != null) {
                // It's a single song segment, add it directly.
                addItemsToQueueUseCase(listOf(item))
                Timber.d("AddOrFetchAndAddUseCase: Added single segment '${item.title}' to queue.")
                Result.success("Added '${item.title}' to queue.")
            } else {
                // It's a full video, fetch its details to get all segments.
                Timber.d("AddOrFetchAndAddUseCase: Item is a full video ('${item.title}'). Fetching segments...")
                val videoResult = holodexRepository.getVideoWithSongs(item.videoId, forceRefresh = true)

                videoResult.fold(
                    onSuccess = { videoWithSongs ->
                        if (!videoWithSongs.songs.isNullOrEmpty()) {
                            // Video has segments, add them all.
                            val segmentItems = videoWithSongs.songs.map { song ->
                                song.toPlaybackItem(videoWithSongs)
                            }
                            addItemsToQueueUseCase(segmentItems)
                            Timber.d("AddOrFetchAndAddUseCase: Added ${segmentItems.size} segments from '${item.title}' to queue.")
                            Result.success("Added ${segmentItems.size} songs from '${item.title}' to queue.")
                        } else {
                            // Video has no segments, add the full video itself.
                            addItemsToQueueUseCase(listOf(item))
                            Timber.d("AddOrFetchAndAddUseCase: Video has no segments. Added full video '${item.title}' to queue.")
                            Result.success("Added '${item.title}' to queue.")
                        }
                    },
                    onFailure = {
                        Timber.e(it, "AddOrFetchAndAddUseCase: Failed to fetch video details for ${item.videoId}.")
                        Result.failure(it)
                    }
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "AddOrFetchAndAddUseCase: An unexpected error occurred.")
            Result.failure(e)
        }
    }
}