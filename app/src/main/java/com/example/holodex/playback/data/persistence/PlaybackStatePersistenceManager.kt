// File: java/com/example/holodex/playback/data/persistence/PlaybackStatePersistenceManager.kt
package com.example.holodex.playback.data.persistence

import com.example.holodex.playback.domain.model.PersistedPlaybackData
import com.example.holodex.playback.domain.repository.PlaybackStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class PlaybackStatePersistenceManager(
    private val playbackStateRepository: PlaybackStateRepository,
    private val externalScope: CoroutineScope
) {
    companion object {
        private const val TAG = "PlaybackStatePersistMgr"
        private const val DEFAULT_SAVE_DELAY_MS = 750L
    }

    private var saveStateJob: Job? = null

    suspend fun saveState(data: PersistedPlaybackData) {
        Timber.d("$TAG: Saving state directly. Queue ID: ${data.queueId}, Items: ${data.queueItems.size}")
        try {
            playbackStateRepository.saveState(data)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during direct state save.")
        }
    }

    suspend fun loadState(): PersistedPlaybackData? {
        Timber.d("$TAG: Loading state.")
        return try {
            playbackStateRepository.loadState()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error loading state.")
            null
        }
    }

    suspend fun clearState() {
        Timber.d("$TAG: Clearing state.")
        try {
            saveStateJob?.cancel()
            playbackStateRepository.clearState()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error clearing state.")
        }
    }

    fun scheduleSave(data: PersistedPlaybackData, delayMs: Long = DEFAULT_SAVE_DELAY_MS) {
        saveStateJob?.cancel()
        saveStateJob = externalScope.launch {
            delay(delayMs)
            if (isActive) {
                Timber.d("$TAG: Scheduled save executing after delay. Queue ID: ${data.queueId}")
                saveState(data)
            } else {
                Timber.d("$TAG: Scheduled save cancelled before execution.")
            }
        }
        Timber.v("$TAG: State save scheduled with delay: $delayMs ms.")
    }
}