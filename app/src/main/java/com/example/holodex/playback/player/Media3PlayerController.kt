// File: java/com/example/holodex/playback/player/Media3PlayerController.kt
package com.example.holodex.playback.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.util.EventLogger
import com.example.holodex.playback.domain.model.DomainPlaybackState
import com.example.holodex.playback.util.PlayerStateMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class PlayerMediaItemTransition(val mediaItem: MediaItem?, val newIndex: Int, val reason: Int)
data class PlayerErrorEvent(val error: PlaybackException)
data class PlayerTimelineChangedEvent(val timeline: Timeline, val reason: Int)
data class PlayerIsPlayingChangedEvent(val isPlaying: Boolean)
data class PlayerDiscontinuityEvent(
    val oldPosition: Player.PositionInfo,
    val newPosition: Player.PositionInfo,
    val reason: Int
)

@UnstableApi
class Media3PlayerController(
    val exoPlayer: ExoPlayer,
    private val preloadManager: DefaultPreloadManager
) : Player.Listener {

    companion object {
        private const val TAG = "Media3PlayerController"
    }

    private val eventLogger: EventLogger = EventLogger(TAG)
    private val controllerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _playerPlaybackStateFlow = MutableStateFlow(DomainPlaybackState.IDLE)
    val playerPlaybackStateFlow: StateFlow<DomainPlaybackState> = _playerPlaybackStateFlow.asStateFlow()

    private val _mediaItemTransitionEventFlow = MutableSharedFlow<PlayerMediaItemTransition>(replay = 0)
    val mediaItemTransitionEventFlow: SharedFlow<PlayerMediaItemTransition> = _mediaItemTransitionEventFlow.asSharedFlow()

    private val _isPlayingChangedEventFlow = MutableSharedFlow<PlayerIsPlayingChangedEvent>(replay = 0)
    val isPlayingChangedEventFlow: SharedFlow<PlayerIsPlayingChangedEvent> = _isPlayingChangedEventFlow.asSharedFlow()

    private val _discontinuityEventFlow = MutableSharedFlow<PlayerDiscontinuityEvent>(replay = 0)
    val discontinuityEventFlow: SharedFlow<PlayerDiscontinuityEvent> = _discontinuityEventFlow.asSharedFlow()

    init {
        exoPlayer.addListener(this)
        exoPlayer.addAnalyticsListener(eventLogger)
        setupAudioAttributes()

        _playerPlaybackStateFlow.value = PlayerStateMapper.mapExoPlayerStateToDomain(exoPlayer.playbackState, exoPlayer.playWhenReady)
    }

    private fun setupAudioAttributes() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        exoPlayer.setAudioAttributes(audioAttributes, true)
    }

    fun play() {
        if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
            exoPlayer.prepare()
        }
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekToDefaultPosition()
        }
        exoPlayer.play()
    }

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    fun pause() = exoPlayer.pause()
    fun seekTo(positionMs: Long) {
        if (exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    fun seekToItem(itemIndex: Int, positionMs: Long) {
        if (exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) {
            exoPlayer.seekTo(itemIndex, positionMs.coerceAtLeast(0L))
        }
    }

    fun setRepeatMode(@Player.RepeatMode exoPlayerMode: Int) {
        exoPlayer.repeatMode = exoPlayerMode
    }

    fun setMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        updatePreloadManagerPlaylist(items)
        if (items.isNotEmpty()) {
            exoPlayer.setMediaItems(items, startIndex, startPositionMs)
            exoPlayer.prepare()
        } else {
            _playerPlaybackStateFlow.value = PlayerStateMapper.mapExoPlayerStateToDomain(exoPlayer.playbackState, exoPlayer.playWhenReady)
        }
    }

    fun clearMediaItemsAndStop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        preloadManager.reset()
    }

    internal  fun getMediaItemsFromPlayerTimeline(): List<MediaItem> {
        val timeline = exoPlayer.currentTimeline
        if (timeline.isEmpty) return emptyList()
        return (0 until timeline.windowCount).map {
            timeline.getWindow(it, Timeline.Window()).mediaItem
        }
    }

    private fun updatePreloadManagerPlaylist(mediaItems: List<MediaItem>) {
        try {
            preloadManager.reset()
            if (mediaItems.isEmpty()) {
                preloadManager.setCurrentPlayingIndex(C.INDEX_UNSET)
                return
            }
            val preloadableItems = mediaItems.filter { it.localConfiguration?.uri?.scheme != "placeholder" }
            preloadableItems.forEach { item ->
                val originalIndex = mediaItems.indexOf(item)
                if (originalIndex != -1) {
                    preloadManager.add(item, originalIndex)
                }
            }
            preloadManager.setCurrentPlayingIndex(exoPlayer.currentMediaItemIndex)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error updating preload manager playlist.")
        }
    }

    fun releasePlayer() {
        exoPlayer.removeAnalyticsListener(eventLogger)
        exoPlayer.removeListener(this)
        exoPlayer.release()
    }

    override fun onPlaybackStateChanged(playbackState: Int) = onStateChanged()
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = onStateChanged()
    private fun onStateChanged() {
        _playerPlaybackStateFlow.value = PlayerStateMapper.mapExoPlayerStateToDomain(exoPlayer.playbackState, exoPlayer.playWhenReady)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        controllerScope.launch { _isPlayingChangedEventFlow.emit(PlayerIsPlayingChangedEvent(isPlaying)) }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val newIndex = exoPlayer.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: -1

        updatePreloadIndex(newIndex)

        controllerScope.launch { _mediaItemTransitionEventFlow.emit(PlayerMediaItemTransition(mediaItem, newIndex, reason)) }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            updatePreloadManagerPlaylist(getMediaItemsFromPlayerTimeline())
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        _playerPlaybackStateFlow.value = DomainPlaybackState.ERROR
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        controllerScope.launch { _discontinuityEventFlow.emit(PlayerDiscontinuityEvent(oldPosition, newPosition, reason)) }
    }

    fun updatePreloadIndex(newIndex: Int) {
        try {
            preloadManager.setCurrentPlayingIndex(newIndex)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error updating preload manager index")
        }
    }
}