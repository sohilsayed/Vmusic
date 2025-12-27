// File: java/com/example/holodex/domain/action/GlobalMediaActionHandler.kt
package com.example.holodex.domain.action

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.holodex.data.db.PlaybackHistoryEntity
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.model.HolodexChannelMin
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.di.ApplicationScope
import com.example.holodex.playback.domain.usecase.AddOrFetchAndAddUseCase
import com.example.holodex.playback.player.PlaybackController
import com.example.holodex.ui.navigation.GlobalUiEvent
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalMediaActionHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackController: PlaybackController,
    private val unifiedRepository: UnifiedVideoRepository,
    private val downloadRepository: DownloadRepository,
    private val unifiedDao: UnifiedDao,
    private val addOrFetchAndAddUseCase: AddOrFetchAndAddUseCase,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val _uiEvents = MutableSharedFlow<GlobalUiEvent>()
    val uiEvents: SharedFlow<GlobalUiEvent> = _uiEvents.asSharedFlow()

    // --- EXISTING SINGLE ITEM PLAY (For simple contexts) ---
    fun onPlay(item: UnifiedDisplayItem) {
        onPlay(listOf(item), 0)
    }

    // --- NEW LIST AWARE PLAY (For creating Queues) ---
    fun onPlay(items: List<UnifiedDisplayItem>, startIndex: Int) {
        appScope.launch {
            try {
                val itemToPlay = items[startIndex]

                // 1. Ensure Metadata Exists (Fixes Foreign Key Crash)
                // When playing from API-fetched lists (like playlists), the item might not be in the DB yet.
                val meta = mapDisplayItemToMetadata(itemToPlay)
                unifiedDao.insertMetadataIgnore(meta)

                // 2. Log to History
                val historyLog = PlaybackHistoryEntity(itemId = itemToPlay.playbackItemId)
                unifiedDao.insertHistoryLog(historyLog)

                // 3. Convert List and Play
                // We map on the Default dispatcher to avoid blocking Main thread for large lists
                val playbackItems = withContext(Dispatchers.Default) {
                    items.map { it.toPlaybackItem() }
                }

                withContext(Dispatchers.Main) {
                    playbackController.loadAndPlay(playbackItems, startIndex)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error playing item queue")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to start playback", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun onQueue(item: UnifiedDisplayItem) {
        appScope.launch {
            // Ensure metadata exists before queuing (Queue logic also persists to DB)
            val meta = mapDisplayItemToMetadata(item)
            unifiedDao.insertMetadataIgnore(meta)

            val playbackItem = item.toPlaybackItem()
            val result = addOrFetchAndAddUseCase(playbackItem)

            withContext(Dispatchers.Main) {
                result.onSuccess { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                result.onFailure { Toast.makeText(context, "Failed to queue", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun onToggleLike(item: UnifiedDisplayItem) {
        appScope.launch {
            val playbackItem = item.toPlaybackItem()
            unifiedRepository.toggleLike(playbackItem)
        }
    }

    fun onDownload(item: UnifiedDisplayItem) {
        appScope.launch {
            try {
                val channel = HolodexChannelMin(
                    id = item.channelId,
                    name = item.artistText,
                    photoUrl = null,
                    englishName = null,
                    org = null,
                    type = "vtuber"
                )

                val videoItem = HolodexVideoItem(
                    id = item.navigationVideoId, // Use clean ID
                    title = if(item.isSegment) "Parent Video" else item.title,
                    type = "stream",
                    topicId = null,
                    availableAt = "",
                    publishedAt = null,
                    duration = 0,
                    status = "past",
                    channel = channel,
                    songcount = 0,
                    description = null,
                    songs = null
                )

                val song = HolodexSong(
                    name = item.title,
                    start = item.songStartSec ?: 0,
                    end = item.songEndSec ?: item.durationText.length, // Approximate backup
                    itunesId = null,
                    artUrl = item.artworkUrls.firstOrNull(),
                    originalArtist = item.originalArtist,
                    videoId = item.navigationVideoId // Use clean ID
                )

                downloadRepository.startDownload(videoItem, song)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download started for '${item.title}'", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Timber.e(e, "Download start failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error starting download", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun onDeleteDownload(item: UnifiedDisplayItem) {
        appScope.launch {
            downloadRepository.deleteDownloadById(item.playbackItemId)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onAddToPlaylist(item: UnifiedDisplayItem) {
        appScope.launch {
            _uiEvents.emit(GlobalUiEvent.ShowPlaylistDialog(item))
        }
    }

    fun onNavigateToVideo(videoId: String) {
        appScope.launch {
            _uiEvents.emit(GlobalUiEvent.NavigateToVideo(videoId))
        }
    }

    fun onNavigateToChannel(channelId: String) {
        appScope.launch {
            _uiEvents.emit(GlobalUiEvent.NavigateToChannel(channelId))
        }
    }

    fun onShare(item: UnifiedDisplayItem) {
        try {
            val url = if (item.isSegment && item.songStartSec != null) {
                "https://music.holodex.net/watch/${item.navigationVideoId}/${item.songStartSec}"
            } else {
                "https://music.holodex.net/watch/${item.navigationVideoId}"
            }

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, url)
                type = "text/plain"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val shareIntent = Intent.createChooser(sendIntent, null).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to share item")
            appScope.launch {
                _uiEvents.emit(GlobalUiEvent.ShowToast("Could not share item"))
            }
        }
    }

    private fun mapDisplayItemToMetadata(item: UnifiedDisplayItem): UnifiedMetadataEntity {
        val bestArt = item.artworkUrls.firstOrNull { !it.isNullOrBlank() }

        // Calculate duration properly
        val duration = if (item.songEndSec != null && item.songStartSec != null) {
            (item.songEndSec - item.songStartSec).toLong()
        } else {
            0L
        }

        return UnifiedMetadataEntity(
            id = item.playbackItemId,
            title = item.title,
            artistName = item.artistText,
            type = if (item.isSegment) "SEGMENT" else "VIDEO",
            specificArtUrl = bestArt,
            uploaderAvatarUrl = null,
            duration = duration,
            channelId = item.channelId,
            parentVideoId = if (item.isSegment && item.videoId != item.playbackItemId) item.videoId else null,
            startSeconds = item.songStartSec?.toLong(),
            endSeconds = item.songEndSec?.toLong(),
            org = if(item.isExternal) "External" else null,
            lastUpdatedAt = System.currentTimeMillis()
        )
    }
    fun onRetryDownload(item: UnifiedDisplayItem) {
        appScope.launch {
            if (item.downloadStatus == "EXPORT_FAILED") {
                downloadRepository.retryExport(item.playbackItemId)
            } else {
                downloadRepository.retryDownload(item.playbackItemId)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Retrying download...", Toast.LENGTH_SHORT).show()
            }
        }
    }
}