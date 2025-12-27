package com.example.holodex.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.data.model.HolodexChannelMin
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.VideoRepository
import com.example.holodex.playback.domain.usecase.AddItemsToQueueUseCase
import com.example.holodex.playback.player.PlaybackController
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.IdUtil
import com.example.holodex.util.PaletteExtractor
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import org.mobilenativefoundation.store.store5.StoreReadResponse
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VideoDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    private val playbackController: PlaybackController,
    private val addItemsToQueueUseCase: AddItemsToQueueUseCase,
    private val downloadRepository: DownloadRepository,
    private val paletteExtractor: PaletteExtractor
) : ViewModel() {

    companion object {
        const val VIDEO_ID_ARG = "videoId"
    }

    private val rawArg: String = savedStateHandle.get<String>(VIDEO_ID_ARG) ?: ""
    val videoId: String = IdUtil.extractVideoId(rawArg)

    // UI State
    private val _videoItem = MutableStateFlow<UnifiedDisplayItem?>(null)
    val videoItem: StateFlow<UnifiedDisplayItem?> = _videoItem.asStateFlow()

    private val _songItems = MutableStateFlow<ImmutableList<UnifiedDisplayItem>>(persistentListOf())
    val songItems: StateFlow<ImmutableList<UnifiedDisplayItem>> = _songItems.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _transientMessage = MutableStateFlow<String?>(null)
    val transientMessage: StateFlow<String?> = _transientMessage.asStateFlow()

    private val _dynamicTheme = MutableStateFlow(DynamicTheme.default(Color.Black, Color.White))
    val dynamicTheme: StateFlow<DynamicTheme> = _dynamicTheme.asStateFlow()

    init {
        if (videoId.isNotBlank()) {
            observeData()
        } else {
            _isLoading.value = false
            _error.value = "Invalid Video ID"
        }
    }

    private fun observeData() {
        // FIX: Set refresh = true to force API fetch.
        // This ensures we get the songs list even if the video is already in DB from a Search result.
        combine(
            videoRepository.getVideo(videoId, refresh = true),
            videoRepository.getVideoSegments(videoId)
        ) { videoResponse, segments ->

            when (videoResponse) {
                is StoreReadResponse.Data -> {
                    val item = videoResponse.value
                    _videoItem.value = item
                    updateTheme(item.artworkUrls.firstOrNull())
                    _isLoading.value = false
                }
                is StoreReadResponse.Error -> {
                    if (_videoItem.value == null) {
                        _error.value = videoResponse.errorMessageOrNull() ?: "Error loading video"
                    }
                    _isLoading.value = false
                }
                is StoreReadResponse.Loading -> {
                    if (_videoItem.value == null) _isLoading.value = true
                }
                else -> {}
            }

            if (segments.isNotEmpty()) {
                _songItems.value = segments.toImmutableList()
            } else {
                val currentVideo = _videoItem.value
                if (currentVideo != null) {
                    val virtualPlaybackId = IdUtil.createCompositeId(currentVideo.navigationVideoId, 0)

                    val virtualItem = currentVideo.copy(
                        stableId = "virtual_segment_${currentVideo.navigationVideoId}",
                        playbackItemId = virtualPlaybackId,
                        isSegment = true,
                        title = currentVideo.title,
                        durationText = currentVideo.durationText,
                        songStartSec = 0,
                        songEndSec = null
                    )
                    _songItems.value = persistentListOf(virtualItem)
                } else {
                    _songItems.value = persistentListOf()
                }
            }
        }.launchIn(viewModelScope)
    }

    private suspend fun updateTheme(url: String?) {
        if (url.isNullOrBlank()) return
        _dynamicTheme.value = paletteExtractor.extractThemeFromUrl(
            url,
            DynamicTheme.default(Color.Black, Color.White)
        )
    }

    fun playSegment(item: UnifiedDisplayItem) {
        val playbackItem = item.toPlaybackItem()
        playbackController.loadAndPlay(listOf(playbackItem))
    }

    fun playAllSegments() {
        val items = _songItems.value
        if (items.isNotEmpty()) {
            val playbackItems = items.map { it.toPlaybackItem() }
            playbackController.loadAndPlay(playbackItems)
        }
    }

    fun addAllSegmentsToQueue() {
        val items = _songItems.value
        if (items.isNotEmpty()) {
            val playbackItems = items.map { it.toPlaybackItem() }
            addItemsToQueueUseCase(playbackItems)
            _transientMessage.value = "Added ${items.size} songs to queue"
        }
    }

    fun downloadAllSegments() {
        val videoUi = _videoItem.value
        val songsUi = _songItems.value

        if (videoUi == null || songsUi.isEmpty()) {
            _error.value = "No segments available to download."
            return
        }

        viewModelScope.launch {
            try {
                _transientMessage.value = "Queueing ${songsUi.size} songs for download..."

                val channel = HolodexChannelMin(
                    id = videoUi.channelId,
                    name = videoUi.artistText,
                    photoUrl = null,
                    englishName = null,
                    org = null,
                    type = "vtuber"
                )

                val parentVideo = HolodexVideoItem(
                    id = videoUi.navigationVideoId,
                    title = videoUi.title,
                    type = "stream",
                    topicId = null,
                    availableAt = "",
                    publishedAt = null,
                    duration = 0,
                    status = "past",
                    channel = channel,
                    songcount = songsUi.size,
                    description = null,
                    songs = null
                )

                songsUi.forEach { item ->
                    val song = HolodexSong(
                        name = item.title,
                        start = item.songStartSec ?: 0,
                        end = item.songEndSec ?: 0,
                        itunesId = null,
                        artUrl = item.artworkUrls.firstOrNull(),
                        originalArtist = item.originalArtist,
                        videoId = item.navigationVideoId
                    )
                    downloadRepository.startDownload(parentVideo, song)
                }
            } catch (e: Exception) {
                Timber.e(e, "Bulk download failed")
                _error.value = "Error starting downloads"
            }
        }
    }

    fun clearError() { _error.value = null }
    fun clearTransientMessage() { _transientMessage.value = null }
}