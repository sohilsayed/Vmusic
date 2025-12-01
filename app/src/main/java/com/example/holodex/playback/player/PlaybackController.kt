package com.example.holodex.playback.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.playback.data.mapper.MediaItemMapper
import com.example.holodex.playback.data.model.PlaybackDao
import com.example.holodex.playback.data.model.PlaybackQueueRefEntity
import com.example.holodex.playback.data.model.PlaybackStateEntity
import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.viewmodel.autoplay.ContinuationManager
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// The One True State Object
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
     private val scope: CoroutineScope
) : Player.Listener {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // The Backup Queue (Source of Truth for Un-Shuffling)
    private var backupQueue: List<PlaybackItem> = emptyList()

    private var saveJob: Job? = null
    private var progressJob: Job? = null

    init {
        exoPlayer.addListener(this)
        restoreState()
    }

    // --- PUBLIC API (Called by ViewModel) ---

    fun loadAndPlay(items: List<PlaybackItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        scope.launch {
            // 1. Resolve Local Files (THE FIX)
            val resolvedItems = resolveLocalPaths(items)

            // 2. Update State & Backup
            backupQueue = resolvedItems

            // 3. Set to Player (on Main Thread)
            withContext(Dispatchers.Main) {
                val mediaItems = resolvedItems.mapNotNull { mapper.toMedia3MediaItem(it) }
                exoPlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
                exoPlayer.prepare()
                exoPlayer.play()

                _state.update { it.copy(
                    activeQueue = resolvedItems,
                    currentIndex = startIndex,
                    isPlaying = true
                )}
            }
            scheduleSave()
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun skipToNext() {
        if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        scope.launch {
            val currentMode = _state.value.shuffleMode
            val newMode = if (currentMode == DomainShuffleMode.ON) DomainShuffleMode.OFF else DomainShuffleMode.ON

            val currentItem = _state.value.activeQueue.getOrNull(exoPlayer.currentMediaItemIndex)
            val currentPos = exoPlayer.currentPosition

            val newQueue = if (newMode == DomainShuffleMode.ON) {
                // SHUFFLE ON: Randomize backup, keep current song first
                val shuffled = backupQueue.toMutableList()
                shuffled.shuffle()
                if (currentItem != null) {
                    shuffled.remove(currentItem)
                    shuffled.add(0, currentItem)
                }
                shuffled
            } else {
                // SHUFFLE OFF: Restore backup
                backupQueue
            }

            withContext(Dispatchers.Main) {
                val mediaItems = newQueue.mapNotNull { mapper.toMedia3MediaItem(it) }
                val newIndex = newQueue.indexOfFirst { it.id == currentItem?.id }.coerceAtLeast(0)

                exoPlayer.setMediaItems(mediaItems, newIndex, currentPos)

                _state.update { it.copy(
                    activeQueue = newQueue,
                    shuffleMode = newMode,
                    currentIndex = newIndex
                )}
            }
            scheduleSave()
        }
    }

    // --- INTERNAL LOGIC ---

    /**
     * The "Local File Fix".
     * Checks DB for every item. If downloaded, sets streamUri = file:// and clears clipping.
     */
    private suspend fun resolveLocalPaths(items: List<PlaybackItem>): List<PlaybackItem> {
        val itemIds = items.map { it.id }
        // Use the Batch Query we planned
        val downloads = unifiedDao.getCompletedDownloadsBatch(itemIds)
        val downloadMap = downloads.associateBy { it.itemId }

        return items.map { item ->
            val localDownload = downloadMap[item.id]
            if (localDownload != null && !localDownload.localFilePath.isNullOrBlank()) {
                item.copy(
                    streamUri = localDownload.localFilePath,
                    clipStartSec = null, // DISABLE CLIPPING for local files
                    clipEndSec = null
                )
            } else {
                item // Network item, keep clipping
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

            // Resolve paths again in case files moved/deleted while app was closed
            val resolvedActive = resolveLocalPaths(activeItems)
            backupQueue = resolveLocalPaths(backupItems)

            withContext(Dispatchers.Main) {
                if (resolvedActive.isNotEmpty()) {
                    val mediaItems = resolvedActive.mapNotNull { mapper.toMedia3MediaItem(it) }
                    exoPlayer.setMediaItems(mediaItems, savedState.currentIndex, savedState.positionMs)
                    exoPlayer.repeatMode = savedState.repeatMode
                    // Don't auto-play on restore
                    exoPlayer.prepare()

                    _state.update { it.copy(
                        activeQueue = resolvedActive,
                        currentIndex = savedState.currentIndex,
                        shuffleMode = if(savedState.isShuffleEnabled) DomainShuffleMode.ON else DomainShuffleMode.OFF,
                        repeatMode = when(savedState.repeatMode) {
                            1 -> DomainRepeatMode.ONE
                            2 -> DomainRepeatMode.ALL
                            else -> DomainRepeatMode.NONE
                        }
                    )}
                }
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(2000) // Debounce 2s

            // --- FIX: Read state on Main Thread first ---
            val playerStateSnapshot = withContext(Dispatchers.Main) {
                Triple(
                    exoPlayer.currentMediaItemIndex,
                    exoPlayer.currentPosition,
                    exoPlayer.repeatMode
                )
            }

            // Now pass the snapshot to the background save function
            saveStateToDb(playerStateSnapshot)
        }
    }

    private suspend fun saveStateToDb(snapshot: Triple<Int, Long, Int>) {
        val (index, position, repeat) = snapshot
        val s = _state.value

        // 1. Prepare Metadata
        // We combine active and backup queues, and distinct by ID to avoid duplicate work
        val allItems = (s.activeQueue + backupQueue).distinctBy { it.id }

        val metadataEntities = allItems.map { item ->
            val isSegment = item.songId != null
            val type = if (isSegment) "SEGMENT" else "VIDEO"
            // If it's a segment, the parent video ID is usually videoId.
            // If it's a video, parentVideoId is null.
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

        // 2. Batch Insert (Optimized)
        // We use IGNORE strategy. If it exists, we do nothing (fast). If missing, we insert (fixes FK crash).
        if (metadataEntities.isNotEmpty()) {
            unifiedDao.insertMetadataBatch(metadataEntities)
        }

        // 3. Save Queue Refs
        val stateEntity = PlaybackStateEntity(
            currentIndex = index,
            positionMs = position,
            isShuffleEnabled = s.shuffleMode == DomainShuffleMode.ON,
            repeatMode = repeat
        )

        val activeRefs = s.activeQueue.mapIndexed { i, item ->
            PlaybackQueueRefEntity(i, item.id, isBackup = false)
        }
        val backupRefs = backupQueue.mapIndexed { i, item ->
            PlaybackQueueRefEntity(i, item.id, isBackup = true)
        }

        playbackDao.saveFullState(stateEntity, activeRefs, backupRefs)
    }

    // --- PLAYER LISTENERS ---

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY && _state.value.isPlaying) {
            startProgressLoop()
        } else {
            stopProgressLoop()
        }

        // Update loading state
        _state.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _state.update { it.copy(isPlaying = isPlaying) }
        if (isPlaying) startProgressLoop() else stopProgressLoop()
        scheduleSave()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        _state.update { it.copy(currentIndex = exoPlayer.currentMediaItemIndex) }
        scheduleSave()
    }

    private fun startProgressLoop() {
        stopProgressLoop()
        progressJob = scope.launch {
            while (true) {
                // --- FIX: Switch to Main thread to read player properties ---
                val (pos, dur) = withContext(Dispatchers.Main) {
                    exoPlayer.currentPosition to exoPlayer.duration
                }

                _state.update { it.copy(
                    progressMs = pos,
                    durationMs = dur
                )}
                delay(500)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }



    fun addItemsToQueue(items: List<PlaybackItem>) {
        scope.launch {
            // 1. Resolve paths
            val resolvedItems = resolveLocalPaths(items)

            // 2. Add to Backup
            backupQueue = backupQueue + resolvedItems

            // 3. Add to Player
            withContext(Dispatchers.Main) {
                val mediaItems = resolvedItems.mapNotNull { mapper.toMedia3MediaItem(it) }
                exoPlayer.addMediaItems(mediaItems)

                // State update happens via onTimelineChanged listener automatically,
                // but we trigger save explicitly
                scheduleSave()
            }
        }
    }

    fun addItemsToQueue(items: List<PlaybackItem>, index: Int? = null) {
        if (items.isEmpty()) return

        scope.launch {
            // 1. Resolve paths
            val resolvedItems = resolveLocalPaths(items)
            val mediaItems = resolvedItems.mapNotNull { mapper.toMedia3MediaItem(it) }

            withContext(Dispatchers.Main) {
                val currentQueueSize = exoPlayer.mediaItemCount
                val currentShuffleMode = _state.value.shuffleMode == DomainShuffleMode.ON

                // Determine Insertion Index for Active Queue
                // If index is provided, use it.
                // If null and Shuffling, add after current item (or at end).
                // If null and Not Shuffling, add to end.
                val insertIndex = index ?: if (currentShuffleMode) {
                    val current = exoPlayer.currentMediaItemIndex
                    if (current != C.INDEX_UNSET) current + 1 else currentQueueSize
                } else {
                    currentQueueSize
                }

                // 2. Add to Player (Active Queue)
                exoPlayer.addMediaItems(insertIndex, mediaItems)

                // 3. Add to Backup Queue
                // We always append to the END of the backup queue to preserve "Album Order" logic.
                // If the user wants to reorder the backup, they must unshuffle first.
                backupQueue = backupQueue + resolvedItems

                // 4. Save
                scheduleSave()
            }
        }
    }
    fun loadRadio(radioId: String) {
        scope.launch {
            // 1. Clear existing state
            withContext(Dispatchers.Main) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }

            // 2. Start Radio Session via ContinuationManager
            // Note: You might need to update startRadioSession signature if it depended on Repository
            // Assuming it returns List<PlaybackItem>?
            val initialItems = continuationManager.startRadioSession(radioId, scope, this@PlaybackController)

            if (initialItems.isNullOrEmpty()) {
                Timber.e("PlaybackController: Failed to start radio session for $radioId")
                return@launch
            }

            // 3. Load items like normal playback
            // Resolve local files
            val resolvedItems = resolveLocalPaths(initialItems)
            backupQueue = resolvedItems // Radio doesn't really use backup/unshuffle, but we keep state consistent

            withContext(Dispatchers.Main) {
                val mediaItems = resolvedItems.mapNotNull { mapper.toMedia3MediaItem(it) }
                exoPlayer.setMediaItems(mediaItems, 0, 0L)
                exoPlayer.prepare()
                exoPlayer.play()

                _state.update { it.copy(
                    activeQueue = resolvedItems,
                    currentIndex = 0,
                    isPlaying = true,
                    shuffleMode = DomainShuffleMode.OFF // Radios are usually linear streams
                )}
                scheduleSave()
            }
        }
    }

    fun removeItem(index: Int) {
        // 1. Get the item ID from the Active Queue BEFORE we delete it from the player
        val activeQueue = _state.value.activeQueue
        if (index !in activeQueue.indices) return

        val itemToRemove = activeQueue[index]
        val idToRemove = itemToRemove.id

        // 2. Remove from ExoPlayer (Active Queue)
        // This triggers onTimelineChanged which updates _state.activeQueue
        exoPlayer.removeMediaItem(index)

        // 3. Remove from Backup Queue
        // We reconstruct the list excluding the item with the matching ID
        val currentBackup = backupQueue
        backupQueue = currentBackup.filter { it.id != idToRemove }

        Timber.d("Removed item $idToRemove. Backup size: ${currentBackup.size} -> ${backupQueue.size}")

        scheduleSave()
    }

    fun moveItem(from: Int, to: Int) {
        exoPlayer.moveMediaItem(from, to)
    }

    fun clearQueue() {
        exoPlayer.clearMediaItems()
        exoPlayer.stop()
        backupQueue = emptyList()
        scope.launch { playbackDao.clearQueue() }
        _state.update { PlaybackState() } // Reset state
    }
}