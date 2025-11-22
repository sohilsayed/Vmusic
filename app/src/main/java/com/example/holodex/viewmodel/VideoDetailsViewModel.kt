// File: java/com/example/holodex/viewmodel/VideoDetailsViewModel.kt
package com.example.holodex.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.holodex.data.download.NoDownloadLocationException
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.playback.PlaybackRequestManager
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.usecase.AddOrFetchAndAddUseCase
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.PaletteExtractor
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.getYouTubeThumbnailUrl
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toVirtualSegmentUnifiedDisplayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class VideoDetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val holodexRepository: HolodexRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackRequestManager: PlaybackRequestManager,
    private val addOrFetchAndAddUseCase: AddOrFetchAndAddUseCase,
    private val paletteExtractor: PaletteExtractor
) : ViewModel() {

    companion object {
        const val VIDEO_ID_ARG = "videoId"
        private const val TAG = "VideoDetailsVM"
    }

    val videoId: String = savedStateHandle.get<String>(VIDEO_ID_ARG) ?: ""

    private val _videoDetails = MutableStateFlow<HolodexVideoItem?>(null)
    val videoDetails: StateFlow<HolodexVideoItem?> = _videoDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _transientMessage = MutableStateFlow<String?>(null)
    val transientMessage: StateFlow<String?> = _transientMessage.asStateFlow()

    private val _unifiedSongItems = MutableStateFlow<List<UnifiedDisplayItem>>(emptyList())
    val unifiedSongItems: StateFlow<List<UnifiedDisplayItem>> = _unifiedSongItems.asStateFlow()

    private val _dynamicTheme = MutableStateFlow(DynamicTheme.default(Color.Black, Color.White))
    val dynamicTheme: StateFlow<DynamicTheme> = _dynamicTheme.asStateFlow()

    fun initialize(videoListViewModel: VideoListViewModel) {
        if (videoId.isNotBlank()) {
            viewModelScope.launch {
                _isLoading.value = true
                val prefetchedItem = videoListViewModel.videoItemForDetailScreen

                val isPrefetchedItemComplete = prefetchedItem != null &&
                        prefetchedItem.id == videoId &&
                        (prefetchedItem.channel.org == "External" || prefetchedItem.songs != null)

                if (isPrefetchedItemComplete) {
                    Timber.d("Using COMPLETE pre-fetched video data for $videoId")
                    processItem(prefetchedItem!!) // We know it's not null here
                } else {
                    Timber.d("Pre-fetched data for $videoId is INCOMPLETE or missing. Fetching full details from network.")
                    holodexRepository.getVideoWithSongs(videoId, forceRefresh = false)
                        .onSuccess { processItem(it) }
                        .onFailure { _error.value = "Failed to load video details: ${it.localizedMessage}" }
                }
                _isLoading.value = false
            }
        } else {
            _isLoading.value = false
            _error.value = "Video ID is missing."
        }
    }

    // A single, unified processing function
    private fun processItem(videoItem: HolodexVideoItem) {
        _videoDetails.value = videoItem
        viewModelScope.launch {
            updateTheme(videoItem.id)

            // *** THE CORRECTED LOGIC ***
            // Check if the item is explicitly marked as External OR if it's a Holodex item with no songs.
            val isEffectivelySegmentless = videoItem.channel.org == "External" || videoItem.songs.isNullOrEmpty()

            if (isEffectivelySegmentless) {
                // Process as a single virtual segment
                val likedIds = holodexRepository.likedItemIds.first()
                val isLiked = likedIds.contains(videoItem.id)
                val virtualSegment = videoItem.toVirtualSegmentUnifiedDisplayItem(isLiked, isDownloaded = false)
                _unifiedSongItems.value = listOf(virtualSegment)
            } else {
                // Process the existing song list from the Holodex item
                val songs = videoItem.songs!! // We know it's not null or empty here
                val likedIds = holodexRepository.likedItemIds.first()
                val downloadedIds = downloadRepository.getAllDownloads().first().map { it.videoId }.toSet()
                val unifiedItems = songs.sortedBy { it.start }.map { song ->
                    song.toUnifiedDisplayItem(
                        parentVideo = videoItem,
                        isLiked = likedIds.contains("${videoItem.id}_${song.start}"),
                        isDownloaded = downloadedIds.contains("${videoItem.id}_${song.start}")
                    )
                }
                _unifiedSongItems.value = unifiedItems
            }
        }
    }

    private suspend fun updateTheme(videoId: String) {
        val artworkUrl = getYouTubeThumbnailUrl(videoId, ThumbnailQuality.MAX).firstOrNull()
        _dynamicTheme.value = paletteExtractor.extractThemeFromUrl(
            artworkUrl,
            DynamicTheme.default(Color.Black, Color.White)
        )
    }

    fun playAllSegments() { playSegment(0) }

    fun playSegment(startIndex: Int) {
        viewModelScope.launch {
            // This now works for both cases, as _unifiedSongItems is always populated correctly
            val itemsToPlay = _unifiedSongItems.value.map { it.toPlaybackItem() }
            if (startIndex in itemsToPlay.indices) {
                playbackRequestManager.submitPlaybackRequest(items = itemsToPlay, startIndex = startIndex)
            } else {
                _error.value = "Invalid song index."
            }
        }
    }

    fun addAllSegmentsToQueue() {
        viewModelScope.launch {
            val itemsToAdd = _unifiedSongItems.value
            if (itemsToAdd.isNotEmpty()) {
                addOrFetchAndAddUseCase(itemsToAdd.first().toPlaybackItem().copy(songId = null))
                    .onSuccess { message -> _transientMessage.value = message }
                    .onFailure { error -> _error.value = "Failed to add to queue: ${error.localizedMessage}" }
            }
        }
    }

    fun downloadAllSegments() {
        val video = _videoDetails.value
        if (video == null || video.songs.isNullOrEmpty()) {
            _error.value = "No segments available to download."
            return
        }
        viewModelScope.launch {
            try {
                _transientMessage.value = "Queueing ${video.songs.size} songs for download..."
                video.songs.forEach { song ->
                    downloadRepository.startDownload(video, song)
                }
            } catch (e: Exception) {
                Timber.e(e, "A general error occurred during bulk download initiation.")
                _error.value = "Could not start downloads: An unknown error occurred."
            }
        }
    }

    fun requestDownloadForSongFromPlaybackItem(item: PlaybackItem) {
        viewModelScope.launch {
            val videoResult = holodexRepository.getVideoWithSongs(item.videoId, false).getOrNull()
            if (videoResult == null) {
                _error.value = "Could not find video details to start download."
                return@launch
            }
            val songToDownload = videoResult.songs?.find { it.start.toLong() == item.clipStartSec }
            if (songToDownload == null) {
                _error.value = "Could not find matching song segment to download."
                return@launch
            }
            requestDownloadForSong(videoResult, songToDownload)
        }
    }

    fun requestDownloadForSong(videoItem: HolodexVideoItem, song: HolodexSong) {
        viewModelScope.launch {
            try {
                downloadRepository.startDownload(videoItem, song)
                _transientMessage.value = "Added '${song.name}' to download queue."
            } catch (e: NoDownloadLocationException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = "Could not start download: An unknown error occurred."
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearTransientMessage() {
        _transientMessage.value = null
    }
}