// File: java/com/example/holodex/viewmodel/autoplay/ContinuationManager.kt
package com.example.holodex.viewmodel.autoplay

import com.example.holodex.data.repository.PlaylistRepository
import com.example.holodex.data.repository.UserPreferencesRepository
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.player.PlaybackController
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinuationManager @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val autoplayItemProvider: AutoplayItemProvider
) {

    companion object {
        private const val TAG = "ContinuationManager"
        private const val RADIO_QUEUE_THRESHOLD = 5
    }

    private var autoplayContextItems: List<UnifiedDisplayItem> =
        Collections.synchronizedList(mutableListOf())

    private val _isRadioModeActive = MutableStateFlow(false)
    val isRadioModeActive: StateFlow<Boolean> = _isRadioModeActive.asStateFlow()

    private var currentRadioId: String? = null
    private var radioMonitorJob: Job? = null

    private var isFetchingRadioItems = false

    fun setAutoplayContext(items: List<UnifiedDisplayItem>) {
        if (items.isNotEmpty()) {
            Timber.d("$TAG: Setting autoplay context with ${items.size} items.")
            autoplayContextItems = Collections.synchronizedList(items.toMutableList())
        } else {
            Timber.d("$TAG: Ignoring empty context update.")
        }
    }

    fun clearAutoplayContext() {
        Timber.d("$TAG: Explicitly clearing autoplay context.")
        autoplayContextItems = Collections.synchronizedList(mutableListOf())
    }

    suspend fun startRadioSession(
        radioId: String,
        scope: CoroutineScope,
        controller: PlaybackController
    ): List<PlaybackItem>? {
        endCurrentSession()
        currentRadioId = radioId
        _isRadioModeActive.value = true
        isFetchingRadioItems = false
        Timber.d("$TAG: Starting radio session for ID: $radioId")

        val initialBatch = fetchRadioBatch(radioId)
        if (initialBatch.isNullOrEmpty()) {
            endCurrentSession()
            return null
        }

        radioMonitorJob = scope.launch(Dispatchers.IO) {
            // FIX: Only observe relevant state changes.
            // We map to a Pair of (Queue Size, Current Index) and use distinctUntilChanged.
            // This prevents 'progressMs' updates from re-triggering (and cancelling via collectLatest) the logic.
            controller.state
                .map { it.activeQueue.size to it.currentIndex }
                .distinctUntilChanged()
                .collectLatest { (queueSize, currentIndex) ->
                    // Pass the queue size and index we just extracted, but we need the actual list for logic
                    // It's safe to read the list from the controller state inside the block or pass it if mapped
                    // Better to just access the controller's current state inside for the list content if needed,
                    // or map the whole list (might be heavy for equals check).
                    // Simplest efficient fix:
                    handleQueueStateForRadio(controller.state.value.activeQueue, currentIndex, controller)
                }
        }
        return initialBatch
    }

    fun endCurrentSession() {
        if (currentRadioId != null) {
            Timber.d("$TAG: Ending radio session for ID: $currentRadioId")
        }
        radioMonitorJob?.cancel()
        radioMonitorJob = null
        currentRadioId = null
        _isRadioModeActive.value = false
        isFetchingRadioItems = false
    }

    suspend fun provideAutoplayItems(currentQueue: List<PlaybackItem>): List<PlaybackItem>? {
        val isAutoplayEnabled = userPreferencesRepository.autoplayEnabled.first()
        if (!isAutoplayEnabled) {
            Timber.d("$TAG: Autoplay is disabled by user setting. Not providing items.")
            return null
        }

        Timber.d("$TAG: Autoplay is enabled. Attempting to provide next items.")

        return autoplayItemProvider.provideNextItemsForAutoplay(
            currentScreenItems = autoplayContextItems,
            lastPlayedItemIdInQueue = currentQueue.lastOrNull()?.id
        )
    }

    private suspend fun handleQueueStateForRadio(
        queue: List<PlaybackItem>,
        currentIndex: Int,
        controller: PlaybackController
    ) {
        val radioId = currentRadioId ?: return
        if (isFetchingRadioItems) return

        val songsRemaining = queue.size - (currentIndex + 1)

        if (queue.isNotEmpty() && songsRemaining < RADIO_QUEUE_THRESHOLD) {
            try {
                isFetchingRadioItems = true
                Timber.d("$TAG: Radio queue low ($songsRemaining remaining). Fetching more...")

                val nextBatch = fetchRadioBatch(radioId)
                if (!nextBatch.isNullOrEmpty()) {
                    // Avoid adding duplicates if the API returns items we already have
                    val existingIds = queue.map { it.id }.toSet()
                    val newItems = nextBatch.filter { !existingIds.contains(it.id) }

                    if (newItems.isNotEmpty()) {
                        controller.addItemsToQueue(newItems)
                        Timber.d("$TAG: Added ${newItems.size} items to radio queue.")
                    } else {
                        Timber.w("$TAG: API returned items, but all were duplicates.")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to fetch radio batch.")
            } finally {
                isFetchingRadioItems = false
            }
        }
    }

    private suspend fun fetchRadioBatch(radioId: String): List<PlaybackItem>? {
        val result = playlistRepository.getRadioContent(radioId)
        return result.getOrNull()?.content?.mapNotNull { song ->
            val videoShell = song.toVideoShell(result.getOrNull()?.title ?: "")
            song.toPlaybackItem(videoShell)
        }
    }
}