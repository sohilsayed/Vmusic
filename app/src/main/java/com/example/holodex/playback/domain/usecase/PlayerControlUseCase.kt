package com.example.holodex.playback.domain.usecase

import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.repository.PlaybackRepository
import javax.inject.Inject

class PlayerControlUseCase @Inject constructor(
    private val repository: PlaybackRepository
) {
    suspend fun play() = repository.play()
    suspend fun pause() = repository.pause()
    suspend fun resume() = repository.play()
    suspend fun seekTo(positionSec: Long) = repository.seekTo(positionSec)
    suspend fun skipToNext() = repository.skipToNext()
    suspend fun skipToPrevious() = repository.skipToPrevious()
    suspend fun skipToQueueIndex(index: Int) = repository.skipToQueueItem(index)

    suspend fun setRepeatMode(mode: DomainRepeatMode) = repository.setRepeatMode(mode)
    suspend fun setShuffleMode(mode: DomainShuffleMode) = repository.setShuffleMode(mode)
    suspend fun setScrubbing(isScrubbing: Boolean) = repository.setScrubbing(isScrubbing)

    fun getAudioSessionId(): Int? = repository.getPlayerSessionId()

    // Helper to load a fresh playlist (replaces existing queue)
    suspend fun loadAndPlay(
        items: List<PlaybackItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        shouldShuffle: Boolean = false
    ) {
        if (items.isEmpty()) return
        repository.prepareAndPlay(items, startIndex, startPositionMs, shouldShuffle)
    }
}