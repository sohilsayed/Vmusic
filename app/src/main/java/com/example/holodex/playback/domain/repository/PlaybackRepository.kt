// File: java/com/example/holodex/playback/domain/repository/PlaybackRepository.kt
package com.example.holodex.playback.domain.repository

import com.example.holodex.playback.domain.model.DomainPlaybackProgress
import com.example.holodex.playback.domain.model.DomainPlaybackState
import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.model.PlaybackQueue
import kotlinx.coroutines.flow.Flow

interface PlaybackRepository {
    suspend fun prepareAndPlay(items: List<PlaybackItem>, startIndex: Int, startPositionMs: Long = 0L, shouldShuffle: Boolean = false )
    suspend fun prepareAndPlayRadio(radioId: String)
    suspend fun play()
    suspend fun pause()
    suspend fun seekTo(positionSec: Long) // Changed to Long
    suspend fun skipToNext()
    suspend fun skipToPrevious()
    suspend fun setRepeatMode(mode: DomainRepeatMode)
    suspend fun setShuffleMode(mode: DomainShuffleMode)
    suspend fun addItemToQueue(item: PlaybackItem, index: Int?)
    suspend fun addItemsToQueue(items: List<PlaybackItem>, index: Int? = null)
    suspend fun removeItemFromQueue(index: Int)
    suspend fun reorderQueueItem(fromIndex: Int, toIndex: Int)
    suspend fun clearQueue()
    suspend fun setScrubbing(isScrubbing: Boolean)
    fun observePlaybackState(): Flow<DomainPlaybackState>
    fun observePlaybackProgress(): Flow<DomainPlaybackProgress>
    fun observeCurrentPlayingItem(): Flow<PlaybackItem?>
    fun observePlaybackQueue(): Flow<PlaybackQueue>
    suspend fun skipToQueueItem(index: Int)
    fun release()
    fun getPlayerSessionId(): Int?
}