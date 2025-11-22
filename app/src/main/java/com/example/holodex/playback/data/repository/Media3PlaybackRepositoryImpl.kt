package com.example.holodex.playback.data.repository

import androidx.annotation.OptIn
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
import com.example.holodex.viewmodel.autoplay.ContinuationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

private data class Move(val from: Int, val to: Int)
class PlayerSyncException(message: String, cause: Throwable?) : Exception(message, cause)

@OptIn(UnstableApi::class)
class Media3PlaybackRepositoryImpl @Inject constructor(
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
    private val externalScope: CoroutineScope
) : PlaybackRepository {

    companion object {
        private const val TAG = "M3PlaybackRepo"
        private const val SAVE_DEBOUNCE_MS = 750L
    }

    private var saveStateJob: Job? = null

    // Main Thread Scope for Player Operations
    private val mainScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

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

        // Launch player setup on Main Thread
        mainScope.launch {
            playerController.exoPlayer.playWhenReady = false
            setupPlayerEventListeners()
            setupStateSynchronization()
            loadInitialState()
        }

        // Launch background listeners
        externalScope.launch {
            downloadRepository.downloadCompletedEvents.collectLatest { event ->
                handleDownloadCompletion(event.itemId, event.localFileUri)
            }
        }
    }

    // --- Suspend functions that interact with the player (Main Thread Forced) ---

    override suspend fun prepareAndPlay(
        items: List<PlaybackItem>,
        startIndex: Int,
        startPositionMs: Long,
        shouldShuffle: Boolean
    ) {
        withContext(Dispatchers.Main.immediate) {
            saveStateJob?.cancel()
            continuationManager.endCurrentSession()
            val startWithShuffle = if (shouldShuffle) true else userPreferencesRepository.shuffleOnPlayStartEnabled.first()

            // Optimistic Update
            queueManager.dispatch(QueueAction.SetQueue(items, startIndex, startPositionMs, startWithShuffle))

            // "Lazy" Player Setup
            val queueState = queueManager.playbackQueueFlow.value
            val activeList = queueState.activeList

            if (activeList.isNotEmpty()) {
                // FIX: Use mapNotNull to guarantee non-nullable List<MediaItem>
                val mediaItems = activeList.mapNotNull { mediaItemMapper.toMedia3MediaItem(it) }

                playerController.setMediaItems(mediaItems, queueState.currentIndex, startPositionMs)
                playerController.play()
            }
        }
    }

    override suspend fun prepareAndPlayRadio(radioId: String) {
        withContext(Dispatchers.Main.immediate) {
            saveStateJob?.cancel()
            Timber.tag(TAG).i("RADIO_LOG: prepareAndPlayRadio called with ID: $radioId")

            val initialItems = continuationManager.startRadioSession(radioId, externalScope, this@Media3PlaybackRepositoryImpl)

            if (initialItems.isNullOrEmpty()) {
                Timber.tag(TAG).e("RADIO_LOG: Could not start radio session for $radioId, initial batch was empty.")
                continuationManager.endCurrentSession()
                return@withContext
            }

            queueManager.dispatch(QueueAction.SetQueue(initialItems, 0, 0L, false))
            val activeList = queueManager.playbackQueueFlow.value.activeList

            if (activeList.isNotEmpty()) {
                // FIX: Use mapNotNull
                val mediaItems = activeList.mapNotNull { mediaItemMapper.toMedia3MediaItem(it) }
                playerController.setMediaItems(mediaItems, 0, 0L)
                playerController.play()
            }
        }
    }

    override suspend fun addItemToQueue(item: PlaybackItem, index: Int?) {
        withContext(Dispatchers.Main.immediate) {
            val finalIndex = queueManager.calculateInsertionIndex(item, index)
            // FIX: Check for null explicitly before adding
            val mediaItem = mediaItemMapper.toMedia3MediaItem(item)
            // Although your mapper returns MediaItem (non-null), Kotlin inference might see it as nullable
            // if the compiled bytecode defines it that way from a previous build.
            // The safest way is to treat it as nullable here just in case.
            if (mediaItem != null) {
                playerController.exoPlayer.addMediaItem(finalIndex, mediaItem)
                queueManager.dispatch(QueueAction.AddItem(item, index))
            }
        }
    }

    override suspend fun addItemsToQueue(items: List<PlaybackItem>, index: Int?) {
        withContext(Dispatchers.Main.immediate) {
            val finalIndex = queueManager.calculateInsertionIndex(items.first(), index)
            // FIX: Use mapNotNull
            val mediaItems = items.mapNotNull { mediaItemMapper.toMedia3MediaItem(it) }
            if (mediaItems.isNotEmpty()) {
                playerController.exoPlayer.addMediaItems(finalIndex, mediaItems)
                queueManager.dispatch(QueueAction.AddItems(items, index))
            }
        }
    }

    // --- Simple Delegation (Main Thread Forced) ---

    override suspend fun play() = withContext(Dispatchers.Main.immediate) { playerController.play() }
    override suspend fun pause() = withContext(Dispatchers.Main.immediate) { playerController.pause() }
    override suspend fun seekTo(positionSec: Long) = withContext(Dispatchers.Main.immediate) { playerController.seekTo(positionSec * 1000L) }
    override suspend fun setScrubbing(isScrubbing: Boolean) = withContext(Dispatchers.Main.immediate) { playerController.exoPlayer.setScrubbingModeEnabled(isScrubbing) }

    override suspend fun setRepeatMode(mode: DomainRepeatMode) = withContext(Dispatchers.Main.immediate) { queueManager.dispatch(QueueAction.SetRepeatMode(mode)) }

    override suspend fun setShuffleMode(mode: DomainShuffleMode) = withContext(Dispatchers.Main.immediate) {
        queueManager.dispatch(QueueAction.ToggleShuffle)
        playerController.exoPlayer.shuffleModeEnabled = (queueManager.playbackQueueFlow.value.shuffleMode == DomainShuffleMode.ON)
        syncPlayerWithQueueState(queueManager.playbackQueueFlow.value)
    }

    override suspend fun skipToNext() = withContext(Dispatchers.Main.immediate) {
        if (queueManager.playbackQueueFlow.value.repeatMode == DomainRepeatMode.ONE) {
            playerController.seekTo(0)
        } else {
            queueManager.dispatch(QueueAction.SkipToNext)
        }
    }

    override suspend fun skipToPrevious() = withContext(Dispatchers.Main.immediate) {
        if (playerController.exoPlayer.currentPosition > 3000) {
            playerController.seekTo(0L)
        } else {
            queueManager.dispatch(QueueAction.SkipToPrevious)
        }
    }

    override suspend fun skipToQueueItem(index: Int) = withContext(Dispatchers.Main.immediate) {
        if (index in queueManager.playbackQueueFlow.value.activeList.indices) {
            queueManager.dispatch(QueueAction.SetCurrentIndex(index))
        }
    }

    override suspend fun removeItemFromQueue(index: Int) = withContext(Dispatchers.Main.immediate) {
        playerController.exoPlayer.removeMediaItem(index)
        queueManager.dispatch(QueueAction.RemoveItem(index))
    }

    override suspend fun reorderQueueItem(fromIndex: Int, toIndex: Int) = withContext(Dispatchers.Main.immediate) {
        playerController.exoPlayer.moveMediaItem(fromIndex, toIndex)
        queueManager.dispatch(QueueAction.ReorderItem(fromIndex, toIndex))
    }

    override suspend fun clearQueue() = withContext(Dispatchers.Main.immediate) {
        saveStateJob?.cancel()
        saveStateJob = null
        playerController.clearMediaItemsAndStop()
        queueManager.dispatch(QueueAction.ClearQueue)
        persistenceManager.clearState()
        Timber.tag(TAG).i("Cleared both live and persisted playback state.")
    }

    override fun getPlayerSessionId(): Int? = playerController.exoPlayer.audioSessionId.takeIf { it != -1 }

    override fun release() {
        mainScope.cancel()
        progressTracker.stopTracking()
        playerController.releasePlayer()
    }

    // --- Private Helpers ---

    private suspend fun setupStateSynchronization() {
        queueManager.playbackQueueFlow.collect { queueState ->
            // Sync 1: Current Index
            if (playerController.exoPlayer.currentMediaItemIndex != queueState.currentIndex &&
                queueState.currentIndex in queueState.activeList.indices) {
                Timber.d("$TAG Sync: Player index is out of sync. Seeking to ${queueState.currentIndex}.")
                playerController.seekToItem(queueState.currentIndex, 0L)
            }

            // Sync 2: Repeat Mode
            val newPlayerRepeatMode = PlayerStateMapper.mapDomainRepeatModeToExoPlayer(queueState.repeatMode)
            if (playerController.exoPlayer.repeatMode != newPlayerRepeatMode) {
                Timber.d("$TAG Sync: Updating player repeat mode.")
                playerController.setRepeatMode(newPlayerRepeatMode)
            }
        }
    }

    private suspend fun syncPlayerWithQueueState(queueState: PlaybackQueueState) {
        playerController.exoPlayer.shuffleModeEnabled = (queueState.shuffleMode == DomainShuffleMode.ON)

        val currentTimelineIds = playerController.getMediaItemsFromPlayerTimeline().map { it.mediaId }
        val desiredOrderIds = queueState.activeList.map { it.id }

        if (currentTimelineIds == desiredOrderIds) return

        val moves = calculateOptimalMoves(currentTimelineIds, desiredOrderIds)
        moves.forEach { move ->
            if(move.from < playerController.exoPlayer.mediaItemCount && move.to < playerController.exoPlayer.mediaItemCount) {
                playerController.exoPlayer.moveMediaItem(move.from, move.to)
            }
        }
    }

    private fun calculateOptimalMoves(current: List<String>, desired: List<String>): List<Move> {
        if (current.size != desired.size) return emptyList()
        val moves = mutableListOf<Move>()
        val workingOrder = current.toMutableList()
        for (targetIndex in desired.indices) {
            val targetId = desired[targetIndex]
            val currentIndex = workingOrder.indexOf(targetId)
            if (currentIndex != targetIndex && currentIndex != -1) {
                moves.add(Move(from = currentIndex, to = targetIndex))
                workingOrder.add(targetIndex, workingOrder.removeAt(currentIndex))
            }
        }
        return moves
    }

    private fun setupPlayerEventListeners() {
        playerController.mediaItemTransitionEventFlow.onEach { event ->
            if (event.reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                queueManager.dispatch(QueueAction.SetCurrentIndex(event.newIndex))
            }
            progressTracker.resetProgress()
        }.launchIn(mainScope)

        playerController.playerPlaybackStateFlow.onEach { state ->
            if (state == DomainPlaybackState.ENDED) handlePlaybackEndedByPlayer()
            if (state != DomainPlaybackState.BUFFERING) {
                scheduleSaveState()
            }
        }.launchIn(mainScope)

        playerController.isPlayingChangedEventFlow.onEach { event ->
            if (event.isPlaying) progressTracker.startTracking() else progressTracker.stopTracking()
        }.launchIn(mainScope)
    }

    private suspend fun handlePlaybackEndedByPlayer() {
        val currentQueueState = queueManager.playbackQueueFlow.value
        val autoplayItems = continuationManager.provideAutoplayItems(currentQueueState.activeList)

        if (!autoplayItems.isNullOrEmpty()) {
            addItemsToQueue(autoplayItems, null)
            skipToNext() // Start playing the new items
        } else {
            playerController.pause()
        }
    }

    private fun handleDownloadCompletion(itemId: String, localFileUri: String) {
        val itemToUpdate = queueManager.playbackQueueFlow.value.activeList.find { it.id == itemId } ?: return
        val updatedItem = itemToUpdate.copy(streamUri = localFileUri)
        queueManager.dispatch(QueueAction.UpdateItemInQueue(updatedItem))
    }

    private suspend fun loadInitialState() {
        val persistedData = persistenceManager.loadState() ?: return
        val items = persistedData.queueItems.mapNotNull { mediaItemMapper.toPlaybackItem(it) }

        if (items.isNotEmpty()) {
            val restoredShuffledItems = if (persistedData.shuffleMode == DomainShuffleMode.ON) {
                persistedData.shuffledQueueItemIds?.mapNotNull { id -> items.find { it.id == id } }
            } else null

            queueManager.dispatch(
                QueueAction.SetQueue(
                    items,
                    persistedData.currentIndex,
                    persistedData.currentPositionSec * 1000L,
                    false,
                    persistedData.shuffleMode,
                    persistedData.repeatMode,
                    restoredShuffledItems
                )
            )

            val finalQueueState = queueManager.playbackQueueFlow.value
            if (finalQueueState.activeList.isNotEmpty()) {
                // FIX: MapNotNull for safety
                val mediaItems = finalQueueState.activeList.mapNotNull { mediaItemMapper.toMedia3MediaItem(it) }
                playerController.setMediaItems(mediaItems, finalQueueState.currentIndex, finalQueueState.transientStartPositionMs)
            }
        }
    }

    private suspend fun generatePersistedPlaybackData(): PersistedPlaybackData? {
        val currentQueueState = queueManager.playbackQueueFlow.value
        if (currentQueueState.originalList.isEmpty()) return null

        return PersistedPlaybackData(
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

    private fun scheduleSaveState() {
        saveStateJob?.cancel()
        saveStateJob = mainScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            generatePersistedPlaybackData()?.let {
                persistenceManager.saveState(it)
            }
        }
    }
}