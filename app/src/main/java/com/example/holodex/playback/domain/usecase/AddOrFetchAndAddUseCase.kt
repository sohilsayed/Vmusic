package com.example.holodex.playback.domain.usecase

import com.example.holodex.data.repository.VideoRepository
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import javax.inject.Inject

class AddOrFetchAndAddUseCase @Inject constructor(
    private val videoRepository: VideoRepository, // Inject New Repo
    private val addItemsToQueueUseCase: AddItemsToQueueUseCase
) {
    suspend operator fun invoke(item: PlaybackItem): Result<String> {
        return try {
            if (item.songId != null) {
                addItemsToQueueUseCase(listOf(item))
                Result.success("Added '${item.title}' to queue.")
            } else {
                // It's a full video. Check DB for segments first.
                val segments = videoRepository.getVideoSegmentsSnapshot(item.videoId)

                if (segments.isNotEmpty()) {
                    val playbackItems = segments.map { it.toPlaybackItem() }
                    addItemsToQueueUseCase(playbackItems)
                    Result.success("Added ${playbackItems.size} songs from '${item.title}'")
                } else {

                    val fetchedVideo = videoRepository.getVideoSnapshot(item.videoId)

                    // Check segments again after fetch
                    val freshSegments = videoRepository.getVideoSegmentsSnapshot(item.videoId)

                    if (freshSegments.isNotEmpty()) {
                        val playbackItems = freshSegments.map { it.toPlaybackItem() }
                        addItemsToQueueUseCase(playbackItems)
                        Result.success("Added ${playbackItems.size} songs")
                    } else {
                        addItemsToQueueUseCase(listOf(item))
                        Result.success("Added '${item.title}' to queue.")
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}