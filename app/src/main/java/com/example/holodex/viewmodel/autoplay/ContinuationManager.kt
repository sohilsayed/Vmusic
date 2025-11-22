// File: java/com/example/holodex/viewmodel/autoplay/ContinuationManager.kt
// (Create this new file)

package com.example.holodex.viewmodel.autoplay

import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.UserPreferencesRepository
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.model.PlaybackQueue
import com.example.holodex.playback.domain.repository.PlaybackRepository
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import com.example.holodex.viewmodel.mappers.toVideoShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinuationManager @Inject constructor(
    private val holodexRepository: HolodexRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val autoplayItemProvider: AutoplayItemProvider
) {

    companion object {
        private const val TAG = "ContinuationManager"
        private const val RADIO_QUEUE_THRESHOLD = 5
    }

    private var autoplayContextItems: List<UnifiedDisplayItem> = Collections.synchronizedList(mutableListOf())

    private val _isRadioModeActive = MutableStateFlow(false)
    val isRadioModeActive: StateFlow<Boolean> = _isRadioModeActive.asStateFlow()

    private var currentRadioId: String? = null
    private var radioMonitorJob: Job? = null

    /**
     * Called by ViewModels to provide the current list of items on screen,
     * which will be used as the source for autoplay suggestions.
     */
    fun setAutoplayContext(items: List<UnifiedDisplayItem>) {
        // Only update context if the new list is not empty.
        // This prevents transient empty states from wiping a valid, existing context.
        if (items.isNotEmpty()) {
            Timber.d("$TAG: Setting autoplay context with ${items.size} items.")
            autoplayContextItems = Collections.synchronizedList(items.toMutableList())
        } else {
            Timber.d("$TAG: Ignoring empty context update. Keeping existing context with ${autoplayContextItems.size} items.")
        }
    }

    /**
     * Explicit method for intentionally clearing the autoplay context when a user
     * performs an action that should reset it, like a new search or pull-to-refresh.
     */
    fun clearAutoplayContext() {
        Timber.d("$TAG: Explicitly clearing autoplay context.")
        autoplayContextItems = Collections.synchronizedList(mutableListOf())
    }

    /**
     * Starts a new radio session. Fetches the initial batch of songs and begins monitoring the queue.
     * @return The initial list of PlaybackItems to start the radio.
     */
    suspend fun startRadioSession(radioId: String, scope: CoroutineScope, playbackRepository: PlaybackRepository): List<PlaybackItem>? {
        endCurrentSession() // Stop any previous session
        currentRadioId = radioId
        _isRadioModeActive.value = true
        Timber.d("$TAG: Starting radio session for ID: $radioId")

        val initialBatch = fetchRadioBatch(radioId)
        if (initialBatch.isNullOrEmpty()) {
            Timber.e("$TAG: Failed to fetch initial batch for radio $radioId. Aborting session.")
            endCurrentSession()
            return null
        }

        // Start the job that will monitor the queue
        radioMonitorJob = scope.launch(Dispatchers.IO) {
            Timber.tag(TAG).i("RADIO_LOG: Monitor job LAUNCHED for radio: $radioId")
            playbackRepository.observePlaybackQueue().collectLatest { queue ->
                handleQueueStateForRadio(queue, playbackRepository)
            }
        }
        radioMonitorJob?.invokeOnCompletion {
            Timber.tag(TAG).i("RADIO_LOG: Monitor job COMPLETED/CANCELLED for radio: $radioId")
        }

        return initialBatch
    }

    /**
     * Ends the current radio session, stopping any background monitoring.
     */
    fun endCurrentSession() {
        if (currentRadioId != null) {
            Timber.d("$TAG: Ending radio session for ID: $currentRadioId")
        }
        radioMonitorJob?.cancel()
        radioMonitorJob = null
        currentRadioId = null
        _isRadioModeActive.value = false
    }

    /**
     * Provides the next items to play when a finite queue ends, respecting the user's autoplay setting.
     * @return A list of new PlaybackItems to append, or null if autoplay is disabled or no items are found.
     */
    suspend fun provideAutoplayItems(currentQueue: List<PlaybackItem>): List<PlaybackItem>? {
        val isAutoplayEnabled = userPreferencesRepository.autoplayEnabled.first()
        if (!isAutoplayEnabled) {
            Timber.d("$TAG: Autoplay is disabled by user setting. Not providing items.")
            return null
        }

        Timber.d("$TAG: Autoplay is enabled. Attempting to provide next items.")


        return autoplayItemProvider.provideNextItemsForAutoplay(
            currentScreenItems = autoplayContextItems,
            lastPlayedItemIdInQueue = currentQueue.lastOrNull()?.id,
            { item -> item.toPlaybackItem() }, // Pass the mapper function
            { video, song -> song.toPlaybackItem(video) } // Pass the other mapper function
        )
    }

    private suspend fun handleQueueStateForRadio(queue: PlaybackQueue, playbackRepository: PlaybackRepository) {
        val radioId = currentRadioId ?: return // Session ended

        if (radioId == null) {
            Timber.tag(TAG).d("RADIO_LOG: handleQueueState called but no active radio session. Ignoring.")
            return
        }
        val songsRemaining = queue.items.size - (queue.currentIndex + 1)
        Timber.tag(TAG).d("RADIO_LOG: Queue state update. Songs: ${queue.items.size}, Index: ${queue.currentIndex}, Remaining: $songsRemaining, Threshold: $RADIO_QUEUE_THRESHOLD")

        if (songsRemaining < RADIO_QUEUE_THRESHOLD) {
            Timber.d("$TAG: Radio queue threshold reached ($songsRemaining remaining). Fetching next batch.")
            val nextBatch = fetchRadioBatch(radioId)
            if (!nextBatch.isNullOrEmpty()) {
                playbackRepository.addItemsToQueue(nextBatch)
                Timber.d("$TAG: Appended ${nextBatch.size} new songs to the radio queue.")
            } else {
                Timber.w("$TAG: Fetching next radio batch returned no items.")
            }
        }
    }

    private suspend fun fetchRadioBatch(radioId: String): List<PlaybackItem>? {
        val result = holodexRepository.getRadioContent(radioId)
        return result.getOrNull()?.content?.mapNotNull { song ->
            val videoShell = song.toVideoShell(result.getOrNull()?.title ?: "")
            song.toPlaybackItem(videoShell)
        }
    }
}