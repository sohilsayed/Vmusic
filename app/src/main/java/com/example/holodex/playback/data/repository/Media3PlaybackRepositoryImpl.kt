// File: java/com/example/holodex/playback/data/repository/Media3PlaybackRepositoryImpl.kt
package com.example.holodex.playback.data.repository

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.UserPreferencesRepository
import com.example.holodex.playback.data.mapper.MediaItemMapper
import com.example.holodex.playback.data.persistence.PlaybackStatePersistenceManager
import com.example.holodex.playback.data.queue.PlaybackQueueManager
import com.example.holodex.playback.data.queue.PlaybackQueueState
import com.example.holodex.playback.data.queue.QueueAction
import com.example.holodex.playback.data.source.StreamResolutionCoordinator
import com.example.holodex.playback.data.tracker.PlaybackProgressTracker
import com.example.holodex.playback.domain.model.DomainPlaybackProgress
import com.example.holodex.playback.domain.model.DomainPlaybackState
import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.domain.model.PersistedPlaybackData
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.model.PlaybackQueue
import com.example.holodex.playback.domain.repository.PlaybackRepository
import com.example.holodex.playback.player.Media3PlayerController
import com.example.holodex.playback.util.PlayerStateMapper
import com.example.holodex.playback.util.discontinuityReasonToString
import com.example.holodex.viewmodel.autoplay.ContinuationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private data class Move(val from: Int, val to: Int)
class PlayerSyncException(message: String, cause: Throwable?) : Exception(message, cause)

@OptIn(UnstableApi::class)
class Media3PlaybackRepositoryImpl(
    private val playerController: Media3PlayerController,
    private val queueManager: PlaybackQueueManager,
    private val streamResolver: StreamResolutionCoordinator,
    private val progressTracker: PlaybackProgressTracker,
    private val persistenceManager: PlaybackStatePersistenceManager,
    private val continuationManager: ContinuationManager,
    private val mediaItemMapper: MediaItemMapper,
    private val downloadRepository: DownloadRepository,
    private val holodexRepository: HolodexRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val repositoryScope: CoroutineScope
) : PlaybackRepository {

    companion object {
        private const val TAG = "M3PlaybackRepo"
        private const val SAVE_DEBOUNCE_MS = 750L
    }

    private var saveStateJob: Job? = null
    override fun observePlaybackState(): Flow<DomainPlaybackState> =
        playerController.playerPlaybackStateFlow

    override fun observePlaybackProgress(): Flow<DomainPlaybackProgress> =
        progressTracker.progressFlow

    override fun observePlaybackQueue(): Flow<PlaybackQueue> =
        queueManager.playbackQueueFlow.map { state ->
            PlaybackQueue(
                queueId = "default_queue",
                items = state.activeList,
                currentIndex = state.currentIndex,
                repeatMode = state.repeatMode,
                shuffleMode = state.shuffleMode
            )
        }.distinctUntilChanged()

    override fun observeCurrentPlayingItem(): Flow<PlaybackItem?> =
        queueManager.playbackQueueFlow.map { it.currentItem }.distinctUntilChanged()


    init {
        Timber.d("$TAG: Initializing...")
        playerController.exoPlayer.playWhenReady = false
        setupPlayerEventListeners()
        setupStateSynchronization()
        repositoryScope.launch {
            loadInitialState()
            downloadRepository.downloadCompletedEvents.collectLatest { event ->
                handleDownloadCompletion(event.itemId, event.localFileUri)
            }
        }
    }

    private fun setupStateSynchronization() {
        repositoryScope.launch(Dispatchers.Main.immediate) {
            queueManager.playbackQueueFlow.collect { queueState ->
                // This collector now ONLY handles index and repeat mode changes.
                // Timeline modifications are handled by direct action methods.

                // SYNC 1: Is the player's current track different from our desired track?
                if (playerController.exoPlayer.currentMediaItemIndex != queueState.currentIndex) {
                    Timber.d("$TAG Sync: Player index is out of sync. Seeking to ${queueState.currentIndex}.")
                    if (queueState.currentIndex in queueState.activeList.indices) {
                        playerController.seekToItem(queueState.currentIndex, 0L)
                    }
                }

                // SYNC 2: Is the player's repeat mode out of sync?
                val newPlayerRepeatMode =
                    PlayerStateMapper.mapDomainRepeatModeToExoPlayer(queueState.repeatMode)
                if (playerController.exoPlayer.repeatMode != newPlayerRepeatMode) {
                    Timber.d("$TAG Sync: Updating player repeat mode.")
                    playerController.setRepeatMode(newPlayerRepeatMode)
                }
            }
        }
    }

    private fun resolvePlaceholdersInBackground(
        activeList: List<PlaybackItem>,
        alreadyResolvedIndex: Int
    ) {
        repositoryScope.launch(Dispatchers.IO) {
            activeList.forEachIndexed { index, playbackItem ->
                if (index != alreadyResolvedIndex && isActive) {
                    val resolvedItem = streamResolver.resolveSingleStream(playbackItem)
                    if (resolvedItem != null) {
                        val finalMediaItem = mediaItemMapper.toMedia3MediaItem(resolvedItem)
                        if (finalMediaItem != null) {
                            withContext(Dispatchers.Main) {
                                val currentQueue = queueManager.playbackQueueFlow.value.activeList
                                val playerIndex =
                                    currentQueue.indexOfFirst { it.id == playbackItem.id }
                                if (playerIndex != -1 && playerIndex < playerController.exoPlayer.mediaItemCount) {
                                    playerController.exoPlayer.replaceMediaItem(
                                        playerIndex,
                                        finalMediaItem
                                    )
                                }
                            }
                        }
                    } else {
                        val currentQueue = queueManager.playbackQueueFlow.value.activeList
                        val indexInCurrentQueue =
                            currentQueue.indexOfFirst { it.id == playbackItem.id }
                        if (indexInCurrentQueue != -1) {
                            queueManager.dispatch(QueueAction.RemoveItem(indexInCurrentQueue))
                        }
                    }
                }
            }
        }
    }

    override suspend fun prepareAndPlay(
        items: List<PlaybackItem>,
        startIndex: Int,
        startPositionMs: Long,
        shouldShuffle: Boolean
    ) {
        // --- START OF FIX: Cancel any lingering save job from a previous session ---
        saveStateJob?.cancel()
        // --- END OF FIX ---

        continuationManager.endCurrentSession()
        val startWithShuffle =
            if (shouldShuffle) true else userPreferencesRepository.shuffleOnPlayStartEnabled.first()
        setQueueAndPreparePlayer(items, startIndex, startPositionMs, startWithShuffle)
    }

    override suspend fun prepareAndPlayRadio(radioId: String) {
        // --- START OF FIX: Cancel any lingering save job from a previous session ---
        saveStateJob?.cancel()
        // --- END OF FIX ---

        Timber.tag(TAG).i("RADIO_LOG: prepareAndPlayRadio called with ID: $radioId")
        val initialItems = continuationManager.startRadioSession(radioId, repositoryScope, this)

        if (initialItems.isNullOrEmpty()) {
            Timber.tag(TAG)
                .e("RADIO_LOG: Could not start radio session for $radioId, initial batch was empty.")
            continuationManager.endCurrentSession()
            return
        }

        setQueueAndPreparePlayer(initialItems, 0, 0L, false)
    }

    private suspend fun setQueueAndPreparePlayer(
        items: List<PlaybackItem>,
        startIndex: Int,
        startPositionMs: Long,
        shouldShuffle: Boolean
    ) {
        playerController.stop()

        queueManager.dispatch(
            QueueAction.SetQueue(items, startIndex, startPositionMs, shouldShuffle)
        )

        val finalQueueState = queueManager.playbackQueueFlow.value
        setPlayerTimeline(
            newActiveList = finalQueueState.activeList,
            newCurrentIndex = finalQueueState.currentIndex,
            seekPosition = finalQueueState.transientStartPositionMs
        )
        playerController.play()
    }

    private fun setPlayerTimeline(
        newActiveList: List<PlaybackItem>,
        newCurrentIndex: Int,
        seekPosition: Long
    ) {
        if (newActiveList.isEmpty()) {
            repositoryScope.launch(Dispatchers.Main) {
                playerController.clearMediaItemsAndStop()
            }
            return
        }

        val firstItemToPlay = newActiveList.getOrNull(newCurrentIndex)
        if (firstItemToPlay == null) {
            Timber.e("$TAG: setPlayerTimeline failed, invalid start index $newCurrentIndex for list size ${newActiveList.size}")
            repositoryScope.launch(Dispatchers.Main) {
                playerController.clearMediaItemsAndStop()
            }
            return
        }

        repositoryScope.launch {
            val resolvedFirstItem = streamResolver.resolveSingleStream(firstItemToPlay)

            withContext(Dispatchers.Main) {
                if (resolvedFirstItem != null) {
                    val mediaItems = newActiveList.map {
                        if (it.id == resolvedFirstItem.id) {
                            mediaItemMapper.toMedia3MediaItem(resolvedFirstItem)!!
                        } else {
                            mediaItemMapper.toPlaceholderMediaItem(it)
                        }
                    }
                    playerController.setMediaItems(mediaItems, newCurrentIndex, seekPosition)
                    resolvePlaceholdersInBackground(newActiveList, newCurrentIndex)
                } else {
                    Timber.e("$TAG: Failed to resolve the first item to play. Cannot set timeline.")
                    playerController.clearMediaItemsAndStop()
                }
            }
        }
    }

    override suspend fun play() = playerController.play()
    override suspend fun pause() = playerController.pause()
    override suspend fun seekTo(positionSec: Long) = playerController.seekTo(positionSec * 1000L)

    override suspend fun skipToNext() {
        val currentState = queueManager.playbackQueueFlow.value
        if (currentState.repeatMode == DomainRepeatMode.ONE) {
            playerController.seekTo(0)
        } else {
            queueManager.dispatch(QueueAction.SkipToNext)
        }
    }

    override suspend fun skipToPrevious() {
        if (playerController.exoPlayer.currentPosition > 3000) {
            playerController.seekTo(0L)
            return
        }
        queueManager.dispatch(QueueAction.SkipToPrevious)
    }

    override suspend fun skipToQueueItem(index: Int) {
        if (index in queueManager.playbackQueueFlow.value.activeList.indices) {
            queueManager.dispatch(QueueAction.SetCurrentIndex(index))
        }
    }

    override suspend fun setRepeatMode(mode: DomainRepeatMode) {
        queueManager.dispatch(QueueAction.SetRepeatMode(mode))
    }

    override suspend fun setShuffleMode(mode: DomainShuffleMode) =
        withContext(Dispatchers.Main.immediate) {
            val currentState = queueManager.playbackQueueFlow.value
            if (mode == currentState.shuffleMode) return@withContext

            try {
                // 1. Update domain state first (Single Source of Truth)
                Timber.d("Toggling shuffle. Current mode: ${currentState.shuffleMode}")
                queueManager.dispatch(QueueAction.ToggleShuffle)
                val newQueueState = queueManager.playbackQueueFlow.value
                Timber.d("Domain state updated. New mode: ${newQueueState.shuffleMode}")

                // 2. Sync player state with the new domain state
                syncPlayerWithQueueState(newQueueState)

            } catch (e: Exception) {
                Timber.e(e, "Failed to sync player for shuffle mode change. Rolling back.")
                // Rollback domain state if player sync fails to maintain consistency
                queueManager.dispatch(QueueAction.ToggleShuffle) // Revert the shuffle
                throw PlayerSyncException("Failed to update shuffle mode", e)
            }
        }

    /**
     * Synchronizes the ExoPlayer's state (timeline order and shuffle mode setting)
     * to match the provided definitive queue state from the PlaybackQueueManager.
     */
    private suspend fun syncPlayerWithQueueState(queueState: PlaybackQueueState) {
        // Update the player's shuffle mode setting first
        playerController.exoPlayer.shuffleModeEnabled =
            (queueState.shuffleMode == DomainShuffleMode.ON)

        // Get the current state of the player's timeline
        val currentTimelineIds =
            playerController.getMediaItemsFromPlayerTimeline().map { it.mediaId }
        val desiredOrderIds = queueState.activeList.map { it.id }

        // Only proceed if a reorder is actually necessary
        if (currentTimelineIds == desiredOrderIds) {
            Timber.d("Player timeline already matches desired order. No moves needed.")
            return
        }

        // Calculate the required moves efficiently
        val moves = calculateOptimalMoves(
            current = currentTimelineIds,
            desired = desiredOrderIds
        )

        // Apply the calculated moves to the player
        if (moves.isNotEmpty()) {
            Timber.d("Applying ${moves.size} moves to sync player timeline.")
            applyTimelineChanges(moves)
        }
    }

    /**
     * Calculates the minimal set of moves needed to transform the current list order
     * into the desired list order. This is more robust than a simple loop.
     */
    private fun calculateOptimalMoves(current: List<String>, desired: List<String>): List<Move> {
        if (current.size != desired.size) {
            // This indicates a severe state inconsistency that should be logged.
            Timber.e("Timeline size mismatch! Current: ${current.size}, Desired: ${desired.size}. Cannot calculate moves.")
            // Returning empty list to prevent crash, but this signals a deeper issue.
            return emptyList()
        }

        val moves = mutableListOf<Move>()
        val workingOrder = current.toMutableList()

        // For each position in the desired final list...
        for (targetIndex in desired.indices) {
            val targetId = desired[targetIndex]
            // ...find where that item currently is in our working copy of the timeline.
            val currentIndex = workingOrder.indexOf(targetId)

            // If the item is not where it's supposed to be...
            if (currentIndex != targetIndex && currentIndex != -1) {
                // ...record the move we need to make.
                moves.add(Move(from = currentIndex, to = targetIndex))
                // And simulate that move in our working copy so the next iteration's
                // `indexOf` call is accurate.
                workingOrder.add(targetIndex, workingOrder.removeAt(currentIndex))
            }
        }

        return moves
    }

    /**
     * Applies a list of move operations to the player's timeline.
     * Currently, this is done sequentially as Media3 does not have a batch-move command.
     */
    private suspend fun applyTimelineChanges(moves: List<Move>) {
        // ExoPlayer processes commands on its own thread sequentially.
        // Sending them one after another is the correct approach.
        moves.forEach { move ->
            playerController.exoPlayer.moveMediaItem(move.from, move.to)
        }
    }

    override suspend fun reorderQueueItem(fromIndex: Int, toIndex: Int) {
        // --- START OF REFACTORED REORDER LOGIC ---
        withContext(Dispatchers.Main.immediate) {
            // Directly command the player
            playerController.exoPlayer.moveMediaItem(fromIndex, toIndex)
            // Dispatch to our SSoT to keep it in sync
            queueManager.dispatch(QueueAction.ReorderItem(fromIndex, toIndex))
        }
        // --- END OF REFACTORED REORDER LOGIC ---
    }

    override suspend fun addItemToQueue(item: PlaybackItem, index: Int?) {
        // --- START OF REFACTORED ADD LOGIC ---
        withContext(Dispatchers.Main.immediate) {
            // First, determine the final insertion index from our SSoT's rules
            val currentState = queueManager.playbackQueueFlow.value
            val finalIndex =
                queueManager.calculateInsertionIndex(item, index) // <-- We need to add this helper

            // Directly command the player
            val mediaItem = mediaItemMapper.toPlaceholderMediaItem(item)
            playerController.exoPlayer.addMediaItem(finalIndex, mediaItem)

            // Dispatch to our SSoT to keep it in sync
            queueManager.dispatch(QueueAction.AddItem(item, index))

            // Resolve the new placeholder in the background
            resolvePlaceholdersInBackground(listOf(item), -1)
        }
        // --- END OF REFACTORED ADD LOGIC ---
    }

    // The public addItemsToQueue method remains UNCHANGED. It's for UI-initiated actions.
    override suspend fun addItemsToQueue(items: List<PlaybackItem>, index: Int?) {
        withContext(Dispatchers.Main.immediate) {
            val currentState = queueManager.playbackQueueFlow.value
            val finalIndex = queueManager.calculateInsertionIndex(items.first(), index)

            // This correctly creates placeholders for a responsive UI
            val mediaItems = items.map { mediaItemMapper.toPlaceholderMediaItem(it) }
            playerController.exoPlayer.addMediaItems(finalIndex, mediaItems)

            queueManager.dispatch(QueueAction.AddItems(items, index))

            resolvePlaceholdersInBackground(items, -1)
        }
    }

    // --- NEW: A private helper for adding pre-made MediaItems ---
    /**
     * A private helper to add already-created MediaItems to the player and update the queue.
     * This bypasses the placeholder creation logic and is used for internal operations
     * like autoplay where items are pre-resolved.
     */
    private suspend fun addResolvedMediaItemsToQueue(
        playbackItems: List<PlaybackItem>,
        mediaItems: List<MediaItem>
    ) {
        withContext(Dispatchers.Main.immediate) {
            val currentState = queueManager.playbackQueueFlow.value
            // Autoplay and Radio always append to the end.
            val finalIndex = currentState.activeList.size

            playerController.exoPlayer.addMediaItems(finalIndex, mediaItems)
            queueManager.dispatch(QueueAction.AddItems(playbackItems, null))
        }
    }

    override suspend fun removeItemFromQueue(index: Int) {
        // --- START OF REFACTORED REMOVE LOGIC ---
        withContext(Dispatchers.Main.immediate) {
            // Directly command the player
            playerController.exoPlayer.removeMediaItem(index)
            // Dispatch to our SSoT to keep it in sync
            queueManager.dispatch(QueueAction.RemoveItem(index))
        }
        // --- END OF REFACTORED REMOVE LOGIC ---
    }

    override suspend fun clearQueue() {
        withContext(Dispatchers.Main.immediate) {
            saveStateJob?.cancel()
            saveStateJob = null

            playerController.clearMediaItemsAndStop()
            queueManager.dispatch(QueueAction.ClearQueue)
            persistenceManager.clearState()
            Timber.tag(TAG).i("Cleared both live and persisted playback state.")
        }
    }


    private fun setupPlayerEventListeners() {
        playerController.playerPlaybackStateFlow.onEach { state ->
            if (state == DomainPlaybackState.ENDED) handlePlaybackEndedByPlayer()
            // --- START OF FIX: Only schedule a save on legitimate state changes, not BUFFERING ---
            if (state != DomainPlaybackState.BUFFERING) {
                scheduleSaveState()
            }
            // --- END OF FIX ---
        }.launchIn(repositoryScope)

        playerController.isPlayingChangedEventFlow.onEach { event ->
            if (event.isPlaying) progressTracker.startTracking() else progressTracker.stopTracking()
        }.launchIn(repositoryScope)

        playerController.mediaItemTransitionEventFlow.onEach { event ->
            when (event.reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> progressTracker.resetProgress()
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                    handleAutoTransition(event.newIndex)
                    progressTracker.resetProgress()
                }

                else -> progressTracker.resetProgress()
            }
        }.launchIn(repositoryScope)

        playerController.discontinuityEventFlow.onEach { event ->
            if (event.reason == Player.DISCONTINUITY_REASON_SKIP || event.reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                Timber.d("Player discontinuity event. Reason: ${discontinuityReasonToString(event.reason)}. Logic is now handled by PlaybackProgressTracker.")
            }
        }.launchIn(repositoryScope)
    }

    private fun handleAutoTransition(playerIndex: Int) {
        if (playerIndex != queueManager.playbackQueueFlow.value.currentIndex) {
            queueManager.dispatch(QueueAction.SetCurrentIndex(playerIndex))
        }
    }

    private fun handleDownloadCompletion(itemId: String, localFileUri: String) {
        val itemToUpdate =
            queueManager.playbackQueueFlow.value.activeList.find { it.id == itemId } ?: return
        val updatedItem = itemToUpdate.copy(streamUri = localFileUri)
        queueManager.dispatch(QueueAction.UpdateItemInQueue(updatedItem))
    }

    private suspend fun loadInitialState() {
        val persistedData = persistenceManager.loadState() ?: return
        val domainItems = persistedData.queueItems.mapNotNull { mediaItemMapper.toPlaybackItem(it) }
        val restoredShuffledDomainItems = if (persistedData.shuffleMode == DomainShuffleMode.ON) {
            persistedData.shuffledQueueItemIds?.mapNotNull { id -> domainItems.find { it.id == id } }
        } else {
            null
        }

        // 1. Dispatch to the QueueManager to set our app's internal state.
        queueManager.dispatch(
            QueueAction.SetQueue(
                domainItems,
                persistedData.currentIndex,
                persistedData.currentPositionSec * 1000L,
                false, // Don't re-shuffle on restore
                persistedData.shuffleMode,
                persistedData.repeatMode,
                restoredShuffledDomainItems
            )
        )

        // --- FIX: Add this block to synchronize the player with the newly loaded state ---
        // 2. Get the definitive state that was just set in the QueueManager.
        val finalQueueState = queueManager.playbackQueueFlow.value

        // 3. Build the player's timeline based on this restored state.
        //    We do NOT automatically start playback here; we just prepare the player.
        if (finalQueueState.activeList.isNotEmpty()) {
            Timber.d("$TAG: Restoring player timeline with ${finalQueueState.activeList.size} items.")
            setPlayerTimeline(
                newActiveList = finalQueueState.activeList,
                newCurrentIndex = finalQueueState.currentIndex,
                seekPosition = finalQueueState.transientStartPositionMs
            )
        }
        // --- END OF FIX ---
    }

    private suspend fun generatePersistedPlaybackData(): PersistedPlaybackData? {
        val currentQueueState = queueManager.playbackQueueFlow.value
        if (currentQueueState.originalList.isEmpty()) return null
        return withContext(Dispatchers.Main.immediate) {
            PersistedPlaybackData(
                "default_queue",
                currentQueueState.originalList.map { mediaItemMapper.toPersistedPlaybackItem(it) },
                playerController.exoPlayer.currentMediaItemIndex,
                playerController.exoPlayer.currentPosition / 1000L,
                currentQueueState.currentItem?.id,
                currentQueueState.repeatMode,
                currentQueueState.shuffleMode,
                if (currentQueueState.shuffleMode == DomainShuffleMode.ON) currentQueueState.activeList.map { it.id } else null
            )
        }
    }

    private fun scheduleSaveState() {
        // --- START OF FIX: Implement debouncing with job cancellation ---
        saveStateJob?.cancel()
        saveStateJob = repositoryScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            generatePersistedPlaybackData()?.let {
                persistenceManager.saveState(it) // Use direct save, not scheduled
            }
        }
        // --- END OF FIX ---
    }

    private suspend fun handlePlaybackEndedByPlayer() {
        withContext(Dispatchers.Main.immediate) {
            val currentQueueState = queueManager.playbackQueueFlow.value
            val autoplayItems =
                continuationManager.provideAutoplayItems(currentQueueState.activeList)

            if (!autoplayItems.isNullOrEmpty()) {
                Timber.i("$TAG: Autoplay provided ${autoplayItems.size} new items. Resolving first item before adding to queue.")

                val firstItemToPlay = autoplayItems.first()
                val resolvedFirstItem = streamResolver.resolveSingleStream(firstItemToPlay)

                if (resolvedFirstItem == null) {
                    Timber.e("$TAG: Failed to resolve stream for the first autoplay item. Aborting autoplay.")
                    playerController.pause()
                    return@withContext
                }

                // 1. Create the list of MediaItems to be added. One real, the rest placeholders.
                val newMediaItems = mutableListOf<MediaItem>()
                newMediaItems.add(mediaItemMapper.toMedia3MediaItem(resolvedFirstItem)!!)

                if (autoplayItems.size > 1) {
                    autoplayItems.subList(1, autoplayItems.size).forEach { item ->
                        newMediaItems.add(mediaItemMapper.toPlaceholderMediaItem(item))
                    }
                }

                // 2. Use our new private helper to add the items without re-creating placeholders.
                addResolvedMediaItemsToQueue(autoplayItems, newMediaItems)

                skipToNext()

                if (autoplayItems.size > 1) {
                    resolvePlaceholdersInBackground(
                        autoplayItems.subList(1, autoplayItems.size),
                        -1
                    )
                }

            } else {
                Timber.i("$TAG: Playback ended and no autoplay items were provided. Pausing.")
                playerController.pause()
            }
        }
    }


    override suspend fun setScrubbing(isScrubbing: Boolean) {
        playerController.exoPlayer.setScrubbingModeEnabled(isScrubbing)
    }

    override fun release() {
        repositoryScope.cancel()
        progressTracker.stopTracking()
        playerController.releasePlayer()
    }

    override fun getPlayerSessionId(): Int? =
        playerController.exoPlayer.audioSessionId.takeIf { it != -1 }
}

