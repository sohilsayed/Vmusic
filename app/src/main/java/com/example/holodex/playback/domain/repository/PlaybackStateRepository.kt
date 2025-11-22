// File: java/com/example/holodex/playback/domain/repository/PlaybackStateRepository.kt
package com.example.holodex.playback.domain.repository

import com.example.holodex.playback.domain.model.PersistedPlaybackData

interface PlaybackStateRepository {
    suspend fun saveState(data: PersistedPlaybackData)
    suspend fun loadState(): PersistedPlaybackData?
    suspend fun clearState()
}