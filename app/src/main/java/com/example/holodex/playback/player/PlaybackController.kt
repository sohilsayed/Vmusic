package com.example.holodex.playback.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.repository.UserPreferencesRepository // <--- ADDED
import com.example.holodex.playback.data.mapper.MediaItemMapper
import com.example.holodex.playback.data.model.PlaybackDao
import com.example.holodex.playback.data.model.PlaybackQueueRefEntity
import com.example.holodex.playback.data.model.PlaybackStateEntity
import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.viewmodel.autoplay.ContinuationManager
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val activeQueue: List<PlaybackItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val repeatMode: DomainRepeatMode = DomainRepeatMode.NONE,
    val shuffleMode: DomainShuffleMode = DomainShuffleMode.OFF,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLoading: Boolean = false
)

@UnstableApi
@Singleton
class PlaybackController @Inject constructor(
    val exoPlayer: ExoPlayer,
    private val playbackDao: PlaybackDao,
    private val unifiedDao: UnifiedDao,
    private val mapper: MediaItemMapper,
    private val continuationManager: ContinuationManager,
    private val userPreferencesRepository: UserPreferencesRepository, // <--- INJECTED
    private val scope: CoroutineScope,
    private val mediaControllerManager: MediaControllerManager
) : Player.Listener {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var backupQueue: List<PlaybackItem> = emptyList()

    private var saveJob: Job? = null
    private var progressJob: Job? = null
    private var isClearingQueue = false

    init {
        exoPlayer.addListener(this)
        scope.launch {
            try {
                mediaControllerManager.awaitController()
            } catch (e: Exception) {
                Timber.e(e, "Service connection failed")
            }
        }
        restoreState()
    }

    private fun syncState() {
        val currentIdx = exoPlayer.currentMediaItemIndex
        val isPlaying = exoPlayer.isPlaying
        val isLoading = exoPlayer.playbackState == Player.STATE_BUFFERING ||
                (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.playWhenReady)

        val duration = if (exoPlayer.duration != C.TIME_UNSET) exoPlayer.duration else 0L
        val position = exoPlayer.currentPosition
        val safeIndex = if (currentIdx in _state.value.activeQueue.indices) currentIdx else -1

        // Sync Shuffle/Repeat modes from player
        val shuffleMode = if (exoPlayer.shuffleModeEnabled) DomainShuffleMode.ON else DomainShuffleMode.OFF

        _state.update {
            it.copy(
                currentIndex = safeIndex,
                isPlaying = isPlaying,
                isLoading = isLoading,
                durationMs = duration,
                progressMs = position,
                shuffleMode = shuffleMode
            )
        }
    }

    // --- Listeners ---

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (isClearingQueue) return

        syncState()
        if (playbackState == Player.STATE_READY && _state.value.isPlaying) {
            startProgressLoop()
        } else {
            stopProgressLoop()
            scheduleLightSave()
        }

        // AUTOPLAY TRIGGER (If playback ended)
        if (playbackState == Player.STATE_ENDED) {
            checkAndTriggerAutoplay()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        syncState()
        if (isPlaying) startProgressLoop() else stopProgressLoop()
        savePlayerStateOnly()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (isClearingQueue) return

        syncState()
        scheduleLightSave()

        val currentIdx = exoPlayer.currentMediaItemIndex
        val queueSize = exoPlayer.mediaItemCount

        if (currentIdx != C.INDEX_UNSET && currentIdx >= queueSize - 1) {
            checkAndTriggerAutoplay()
        }
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        syncState()
    }

    // --- Autoplay Logic ---
    private fun checkAndTriggerAutoplay() {
        if (isClearingQueue) {
            Timber.i("Autoplay blocked: Queue is being cleared manually.")
            return
        }
        scope.launch {
            val isAutoplayOn = userPreferencesRepository.autoplayEnabled.first()
            if (!isAutoplayOn) return@launch

            // Ask ContinuationManager for the next song based on context
            val currentQueue = _state.value.activeQueue
            val nextItems = continuationManager.provideAutoplayItems(currentQueue)

            if (!nextItems.isNullOrEmpty()) {
                Timber.i("Autoplay: Adding ${nextItems.size} items to queue.")

                // Add without resolving yet to keep UI snappy, resolve happens in addItemsToQueue
                withContext(Dispatchers.Main) {
                    addItemsToQueue(nextItems)
                }
            }
        }
    }

    // --- Action Implementations ---

    fun loadAndPlay(items: List<PlaybackItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        scope.launch {
            mediaControllerManager.ensureServiceConnection()

            // 1. Check "Shuffle on Play" Preference
            val shuffleOnPlay = userPreferencesRepository.shuffleOnPlayStartEnabled.first()

            val finalItems: List<PlaybackItem>
            val finalStartIndex: Int
            val shouldEnableShuffle: Boolean

            if (shuffleOnPlay && items.size > 1) {
                // SHUFFLE LOGIC:
                // Keep the clicked item (startIndex) at position 0.
                // Shuffle the rest of the list.
                val clickedItem = items[startIndex]
                val rest = items.toMutableList().apply { removeAt(startIndex) }
                rest.shuffle()

                finalItems = listOf(clickedItem) + rest
                finalStartIndex = 0
                shouldEnableShuffle = true
                Timber.d("Shuffle on Play: ON. Rearranged queue.")
            } else {
                // NORMAL LOGIC
                finalItems = items
                finalStartIndex = startIndex
                shouldEnableShuffle = false
            }

            // 2. Resolve Paths
            val resolvedItems = resolveLocalPaths(finalItems)
            backupQueue = resolvedItems // In shuffle mode, backup usually stores original order, but here we destructively shuffled.

            withContext(Dispatchers.Main) {
                _state.update { it.copy(isLoading = true, activeQueue = resolvedItems, currentIndex = finalStartIndex) }

                val mediaItems = resolvedItems.map { mapper.toMedia3MediaItem(it) }

                // Reset player completely
                exoPlayer.stop()
                exoPlayer.clearMediaItems()

                // Set Shuffle Mode BEFORE items
                exoPlayer.shuffleModeEnabled = shouldEnableShuffle

                exoPlayer.setMediaItems(mediaItems, finalStartIndex, startPositionMs)
                exoPlayer.prepare()
                exoPlayer.play()

                syncState()
            }
            saveFullQueueState()
        }
    }

    fun addItemsToQueue(items: List<PlaybackItem>, index: Int? = null) {
        if (items.isEmpty()) return
        scope.launch {
            val resolvedItems = resolveLocalPaths(items)
            val mediaItems = resolvedItems.map { mapper.toMedia3MediaItem(it) }
            if (mediaItems.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                val currentQueueSize = exoPlayer.mediaItemCount
                val isShuffleOn = _state.value.shuffleMode == DomainShuffleMode.ON
                val currentIdx = exoPlayer.currentMediaItemIndex

                val targetIndex = index ?: if (isShuffleOn && currentIdx != C.INDEX_UNSET) {
                    currentIdx + 1
                } else {
                    currentQueueSize
                }

                exoPlayer.addMediaItems(targetIndex, mediaItems)

                val newActiveQueue = _state.value.activeQueue.toMutableList()
                val safeTargetIndex = targetIndex.coerceIn(0, newActiveQueue.size)
                newActiveQueue.addAll(safeTargetIndex, resolvedItems)

                val newBackupQueue = backupQueue.toMutableList()
                if (isShuffleOn) {
                    newBackupQueue.addAll(resolvedItems)
                } else {
                    val safeBackupIndex = safeTargetIndex.coerceIn(0, newBackupQueue.size)
                    newBackupQueue.addAll(safeBackupIndex, resolvedItems)
                }

                backupQueue = newBackupQueue
                _state.update { it.copy(activeQueue = newActiveQueue) }

                syncState()
            }
            saveFullQueueState()
        }
    }

    fun removeItem(index: Int) {
        scope.launch {
            withContext(Dispatchers.Main) {
                val activeQueue = _state.value.activeQueue
                if (index in activeQueue.indices) {
                    val itemToRemove = activeQueue[index]
                    exoPlayer.removeMediaItem(index)
                    backupQueue = backupQueue.filter { it.id != itemToRemove.id }

                    val updatedActive = activeQueue.toMutableList()
                    updatedActive.removeAt(index)
                    _state.update { it.copy(activeQueue = updatedActive) }

                    syncState()
                }
            }
            saveFullQueueState()
        }
    }

    fun moveItem(from: Int, to: Int) {
        scope.launch {
            withContext(Dispatchers.Main) {
                exoPlayer.moveMediaItem(from, to)
                val updatedActive = _state.value.activeQueue.toMutableList()
                val item = updatedActive.removeAt(from)
                updatedActive.add(to, item)
                _state.update { it.copy(activeQueue = updatedActive) }
                syncState()
            }
            saveFullQueueState()
        }
    }

    fun clearQueue() {
        scope.launch {
            isClearingQueue = true // RAISE FLAG

            withContext(Dispatchers.Main) {
                // Stop player first to ensure IDLE state
                exoPlayer.stop()
                exoPlayer.clearMediaItems()

                // Explicitly update state to empty/idle immediately
                _state.update {
                    it.copy(
                        activeQueue = emptyList(),
                        currentIndex = -1,
                        isPlaying = false,
                        progressMs = 0,
                        durationMs = 0,
                        isLoading = false
                    )
                }
            }

            backupQueue = emptyList()
            playbackDao.clearQueue()


            delay(100)
            isClearingQueue = false // LOWER FLAG
        }
    }

    fun loadRadio(radioId: String) {
        scope.launch {
            withContext(Dispatchers.Main) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
            // Ensure service is alive
            mediaControllerManager.ensureServiceConnection()

            val initialItems = continuationManager.startRadioSession(radioId, scope, this@PlaybackController)

            if (!initialItems.isNullOrEmpty()) {
                val resolvedItems = resolveLocalPaths(initialItems)
                backupQueue = resolvedItems

                withContext(Dispatchers.Main) {
                    _state.update { it.copy(isLoading = true) }
                    val mediaItems = resolvedItems.map { mapper.toMedia3MediaItem(it) }
                    exoPlayer.setMediaItems(mediaItems, 0, 0L)
                    exoPlayer.prepare()
                    exoPlayer.play()

                    _state.update { it.copy(
                        activeQueue = resolvedItems,
                        currentIndex = 0,
                        isPlaying = true,
                        shuffleMode = DomainShuffleMode.OFF
                    )}
                    syncState()
                }
                saveFullQueueState()
            }
        }
    }

    private fun startProgressLoop() {
        stopProgressLoop()
        progressJob = scope.launch {
            while (true) {
                val currentPos = withContext(Dispatchers.Main) { exoPlayer.currentPosition }
                _state.update { it.copy(progressMs = currentPos) }
                if (currentPos % 5000 < 1000) {
                    savePlayerStateOnly()
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun scheduleLightSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            savePlayerStateOnly()
        }
    }

    private suspend fun saveFullQueueState() {
        val s = _state.value
        // Don't save empty state on init unless explicitly cleared
        if (s.activeQueue.isEmpty() && backupQueue.isEmpty()) return

        val allItems = (s.activeQueue + backupQueue).distinctBy { it.id }
        val metadataEntities = allItems.map { item ->
            val isSegment = item.songId != null
            val type = if (isSegment) "SEGMENT" else "VIDEO"
            val parentId = if (isSegment) item.videoId else null
            com.example.holodex.data.db.UnifiedMetadataEntity(
                id = item.id,
                title = item.title,
                artistName = item.artistText,
                type = type,
                specificArtUrl = item.artworkUri,
                uploaderAvatarUrl = null,
                duration = item.durationSec,
                channelId = item.channelId,
                description = item.description,
                startSeconds = item.clipStartSec,
                endSeconds = item.clipEndSec,
                parentVideoId = parentId,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
        if (metadataEntities.isNotEmpty()) {
            unifiedDao.insertMetadataBatch(metadataEntities)
        }

        val stateEntity = PlaybackStateEntity(
            currentIndex = s.currentIndex,
            positionMs = s.progressMs,
            isShuffleEnabled = s.shuffleMode == DomainShuffleMode.ON,
            repeatMode = 0
        )
        val activeRefs = s.activeQueue.mapIndexed { i, item ->
            PlaybackQueueRefEntity(i, item.id, isBackup = false)
        }
        val backupRefs = backupQueue.mapIndexed { i, item ->
            PlaybackQueueRefEntity(i, item.id, isBackup = true)
        }

        playbackDao.saveQueueAndState(stateEntity, activeRefs, backupRefs)
    }

    private fun savePlayerStateOnly() {
        scope.launch {
            val (index, pos) = withContext(Dispatchers.Main) {
                exoPlayer.currentMediaItemIndex to exoPlayer.currentPosition
            }
            val s = _state.value
            val stateEntity = PlaybackStateEntity(
                currentIndex = index,
                positionMs = pos,
                isShuffleEnabled = s.shuffleMode == DomainShuffleMode.ON,
                repeatMode = 0
            )
            playbackDao.setPlaybackState(stateEntity)
        }
    }

    fun togglePlayPause() {
        if (!exoPlayer.isPlaying) {
            scope.launch {
                mediaControllerManager.ensureServiceConnection()
                withContext(Dispatchers.Main) { exoPlayer.play() }
            }
        } else {
            exoPlayer.pause()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _state.update { it.copy(progressMs = positionMs) }
    }

    fun skipToNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            _state.update { it.copy(isLoading = true) }
        }
    }

    fun skipToPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
            _state.update { it.copy(isLoading = true) }
        }
    }

    fun toggleShuffle() {
        scope.launch {
            // 1. Get Player State on Main Thread (Explicitly Typed)
            // We use a simple object instead of Triple to avoid ambiguity errors
            val playerSnapshot = withContext(Dispatchers.Main) {
                object {
                    val index = exoPlayer.currentMediaItemIndex
                    val pos = exoPlayer.currentPosition
                    val mode = _state.value.shuffleMode
                }
            }

            val currentIndex = playerSnapshot.index
            val currentPos = playerSnapshot.pos
            val currentMode = playerSnapshot.mode

            // 2. Logic (Background Thread)
            val newMode = if (currentMode == DomainShuffleMode.ON) DomainShuffleMode.OFF else DomainShuffleMode.ON

            // FIX: Access the queue from your internal state, NOT the exoPlayer
            val activeQueue = _state.value.activeQueue

            val itemToKeep = activeQueue.getOrNull(currentIndex)

            val newQueue = if (newMode == DomainShuffleMode.ON) {
                val shuffled = backupQueue.toMutableList()
                shuffled.shuffle()
                if (itemToKeep != null) {
                    // Ensure the currently playing song stays at position 0 so playback continues smoothly
                    shuffled.remove(itemToKeep)
                    shuffled.add(0, itemToKeep)
                }
                shuffled
            } else {
                backupQueue
            }

            // 3. Apply to Player (Main Thread)
            withContext(Dispatchers.Main) {
                val mediaItems = newQueue.mapNotNull { mapper.toMedia3MediaItem(it) }

                // Find where the current song ended up in the new list
                val newIndex = if (itemToKeep != null) {
                    newQueue.indexOfFirst { it.id == itemToKeep.id }.coerceAtLeast(0)
                } else 0

                exoPlayer.shuffleModeEnabled = (newMode == DomainShuffleMode.ON)

                // Reset the player queue to match our manually shuffled list
                exoPlayer.setMediaItems(mediaItems, newIndex, currentPos)

                _state.update {
                    it.copy(activeQueue = newQueue, shuffleMode = newMode, currentIndex = newIndex)
                }

                syncState()
            }
            saveFullQueueState()
        }
    }

    private suspend fun resolveLocalPaths(items: List<PlaybackItem>): List<PlaybackItem> {
        val itemIds = items.map { it.id }
        val downloads = unifiedDao.getCompletedDownloadsBatch(itemIds)
        val downloadMap = downloads.associateBy { it.itemId }

        return items.map { item ->
            val localDownload = downloadMap[item.id]
            if (localDownload != null && !localDownload.localFilePath.isNullOrBlank()) {
                item.copy(
                    streamUri = localDownload.localFilePath,
                    clipStartSec = null,
                    clipEndSec = null
                )
            } else {
                item
            }
        }
    }

    private fun restoreState() {
        scope.launch {
            val savedState = playbackDao.getState() ?: return@launch
            val activeProjections = playbackDao.getActiveQueueWithMetadata()
            val backupProjections = playbackDao.getBackupQueueWithMetadata()

            val activeItems = activeProjections.map { it.toUnifiedDisplayItem().toPlaybackItem() }
            val backupItems = backupProjections.map { it.toUnifiedDisplayItem().toPlaybackItem() }

            val resolvedActive = resolveLocalPaths(activeItems)
            backupQueue = resolveLocalPaths(backupItems)

            withContext(Dispatchers.Main) {
                if (resolvedActive.isNotEmpty()) {
                    val mediaItems = resolvedActive.map { mapper.toMedia3MediaItem(it) }
                    exoPlayer.setMediaItems(mediaItems, savedState.currentIndex, savedState.positionMs)
                    // Don't auto-play on restore
                    exoPlayer.playWhenReady = false
                    exoPlayer.prepare()

                    exoPlayer.shuffleModeEnabled = savedState.isShuffleEnabled

                    _state.update { it.copy(
                        activeQueue = resolvedActive,
                        currentIndex = savedState.currentIndex,
                        shuffleMode = if(savedState.isShuffleEnabled) DomainShuffleMode.ON else DomainShuffleMode.OFF,
                        progressMs = savedState.positionMs
                    )}
                }
            }
        }
    }
}