package com.example.holodex.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.holodex.background.M4AExportWorker
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.DownloadedItemDao
import com.example.holodex.data.db.DownloadedItemEntity
import com.example.holodex.data.db.LikedItemEntity
import com.example.holodex.data.db.ParentVideoMetadataDao
import com.example.holodex.data.db.ParentVideoMetadataEntity
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.repository.DownloadRepository.DownloadCompletedEvent
import com.example.holodex.di.ApplicationScope
import com.example.holodex.di.DownloadCache
import com.example.holodex.di.UpstreamDataSource
import com.example.holodex.service.HolodexDownloadService
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.getYouTubeThumbnailUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
interface DownloadRepository {
    suspend fun startDownload(video: HolodexVideoItem, song: HolodexSong)
    suspend fun cancelDownload(itemId: String)
    suspend fun deleteDownloadById(itemId: String)
    fun getAllDownloads(): Flow<List<DownloadedItemEntity>>
    fun getDownloadById(itemId: String): Flow<DownloadedItemEntity?>
    suspend fun reconcileAllDownloads()
    suspend fun resumeDownload(itemId: String)
    suspend fun retryExportForItem(item: DownloadedItemEntity)
    suspend fun rescanStorageForDownloads()
    val downloadCompletedEvents: SharedFlow<DownloadCompletedEvent>
    suspend fun postDownloadCompletedEvent(event: DownloadCompletedEvent)

    data class DownloadCompletedEvent(val itemId: String, val localFileUri: String)
}

@UnstableApi
@Singleton
@OptIn(UnstableApi::class)
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadedItemDao: DownloadedItemDao,
    private val youtubeStreamRepository: YouTubeStreamRepository,
    @DownloadCache private val downloadCache: SimpleCache,
    @UpstreamDataSource private val upstreamDataSourceFactory: DataSource.Factory,
    private val parentVideoMetadataDao: ParentVideoMetadataDao,
    private val media3DownloadManager: DownloadManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val holodexRepository: HolodexRepository,
    private val workManager: WorkManager
) : DownloadRepository {
    companion object {
        private const val TAG = "DownloadRepositoryImpl"
    }

    private val _downloadCompletedEvents = MutableSharedFlow<DownloadCompletedEvent>()
    override val downloadCompletedEvents: SharedFlow<DownloadCompletedEvent> =
        _downloadCompletedEvents.asSharedFlow()

    override suspend fun startDownload(video: HolodexVideoItem, song: HolodexSong) {
        val itemId = LikedItemEntity.generateSongItemId(video.id, song.start)
        val displayTitle = song.name.ifBlank { video.title }
        val durationSec = (song.end - song.start).toLong()

        val existing = downloadedItemDao.getById(itemId)
        if (existing?.downloadStatus in listOf(
                DownloadStatus.ENQUEUED,
                DownloadStatus.DOWNLOADING,
                DownloadStatus.COMPLETED
            )
        ) {
            Timber.w("$TAG: Download for $itemId is already in progress or completed. Skipping initiation.")
            return
        }

        Timber.d("$TAG: Initiating download process for item: $itemId ('$displayTitle')")

        // The heavy lifting (network calls) is offloaded to the application scope
        // to ensure it survives ViewModel lifecycle changes.
        applicationScope.launch(Dispatchers.IO) {
            try {
                // IMPROVEMENT: Resolve stream and determine format dynamically
                Timber.d("$TAG: Resolving stream for $itemId...")
                val streamDetails = withTimeout(30_000) {
                    youtubeStreamRepository.getAudioStreamDetails(video.id).getOrThrow()
                }
                val streamUri = streamDetails.streamUrl.toUri()

                val targetFormat = "M4A"
                Timber.i("$TAG: Determined target format for $itemId: $targetFormat (source was ${streamDetails.format})")

                // IMPROVEMENT: Use DownloadHelper for efficient partial downloading
                val clipStartTimeMs = song.start * 1000L
                val clipDurationMs = durationSec * 1000L

                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(downloadCache)
                    .setUpstreamDataSourceFactory(upstreamDataSourceFactory)

                val downloadHelperFactory =
                    DownloadHelper.Factory().setDataSourceFactory(cacheDataSourceFactory)
                val mediaItemForHelper = MediaItem.Builder().setUri(streamUri).build()
                val downloadHelper = downloadHelperFactory.create(mediaItemForHelper)

                val downloadRequest = suspendCancellableCoroutine<DownloadRequest> { continuation ->
                    downloadHelper.prepare(object : DownloadHelper.Callback {
                        override fun onPrepared(
                            helper: DownloadHelper,
                            tracksInfoAvailable: Boolean
                        ) {
                            try {
                                val request = helper.getDownloadRequest(
                                    itemId,
                                    displayTitle.toByteArray(StandardCharsets.UTF_8),
                                    clipStartTimeMs,
                                    clipDurationMs
                                )
                                Timber.d("$TAG: DownloadRequest prepared with byte range. ID: ${request.id}")
                                continuation.resume(request)
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        }

                        override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                            continuation.resumeWithException(e)
                        }
                    })
                    continuation.invokeOnCancellation { downloadHelper.release() }
                }
                downloadHelper.release()

                try {
                    Timber.d("$TAG: Proactively caching full video details for ${video.id} before download.")
                    // Force a network refresh to ensure we get the latest song list.
                    // This populates the VideoDao, which is now our source of truth.
                    holodexRepository.getVideoWithSongs(video.id, forceRefresh = true)
                } catch (e: Exception) {
                    // Log the error but DO NOT fail the download.
                    // The download can still proceed, but the user might see an incomplete
                    // song list offline until they go online again.
                    Timber.e(
                        e,
                        "$TAG: Failed to proactively cache video details for ${video.id}. Download will continue."
                    )
                }

                // IMPROVEMENT: Consolidate all metadata before enqueuing
                val sortedSongs = video.songs?.sortedBy { it.start }
                val trackNumber = sortedSongs?.indexOf(song)?.plus(1)

                val entity = DownloadedItemEntity(
                    videoId = itemId,
                    title = displayTitle,
                    artistText = video.channel.name, // Use channel name for Artist tag
                    channelId = video.channel.id ?: "unknown",
                    artworkUrl = song.artUrl ?: video.channel.photoUrl,
                    durationSec = durationSec,
                    localFileUri = null,
                    downloadStatus = DownloadStatus.ENQUEUED,
                    downloadedAt = null,
                    downloadId = null,
                    progress = 0,
                    trackNumber = trackNumber,
                    fileName = "", // Will be set by worker
                    targetFormat = targetFormat // Save the detected format
                )
                downloadedItemDao.insertOrUpdate(entity)

                // Save parent metadata for the worker to retrieve album title
                val parentMetadata = ParentVideoMetadataEntity(
                    videoId = video.id,
                    title = video.title,
                    channelName = video.channel.name,
                    channelId = video.channel.id ?: "unknown",
                    thumbnailUrl = getYouTubeThumbnailUrl(
                        video.id,
                        ThumbnailQuality.HIGH
                    ).firstOrNull(),
                    description = video.description,
                    totalDurationSec = video.duration
                )
                parentVideoMetadataDao.insert(parentMetadata)

                // Enqueue the download with Media3's service
                val requirements =
                    Requirements(Requirements.NETWORK) // Or make this user-configurable
                DownloadService.sendSetRequirements(
                    context,
                    HolodexDownloadService::class.java,
                    requirements,
                    true
                )
                DownloadService.sendAddDownload(
                    context,
                    HolodexDownloadService::class.java,
                    downloadRequest,
                    true
                )

                Timber.i("$TAG: Successfully enqueued download for item ID: $itemId")

            } catch (e: CancellationException) {
                Timber.w("$TAG: Download setup was cancelled for $itemId")
                downloadedItemDao.updateStatus(itemId, DownloadStatus.FAILED)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Critical failure during download setup for $itemId.")
                downloadedItemDao.updateStatus(itemId, DownloadStatus.FAILED)
            }
        }
    }

    override suspend fun resumeDownload(itemId: String) {
        withContext(Dispatchers.IO) {
            Timber.d("DownloadRepository: Attempting to resume download for item ID: $itemId")

            val existingItem = downloadedItemDao.getById(itemId)
            if (existingItem?.downloadStatus == DownloadStatus.PAUSED) {
                // Update status to enqueued and let the download manager handle it
                downloadedItemDao.updateStatus(itemId, DownloadStatus.ENQUEUED)

                // Send resume command to download service with foreground parameter
                DownloadService.sendResumeDownloads(
                    context,
                    HolodexDownloadService::class.java,
                    true
                )

                Timber.i("DownloadRepository: Resume command sent for item ID: $itemId")
            } else {
                Timber.w("DownloadRepository: Cannot resume download for $itemId - current status: ${existingItem?.downloadStatus}")
            }
        }
    }

    override suspend fun cancelDownload(itemId: String) {
        Timber.d("DownloadRepository: Attempting to cancel download for item ID: $itemId")

        WorkManager.getInstance(context).cancelUniqueWork("export_$itemId")

        DownloadService.sendRemoveDownload(
            context,
            HolodexDownloadService::class.java,
            itemId,
            false
        )
    }

    override suspend fun deleteDownloadById(itemId: String) {
        withContext(Dispatchers.IO) {
            Timber.i("DownloadRepository: Starting robust delete for item ID: $itemId")

            val itemToDelete = downloadedItemDao.getById(itemId) ?: run {
                Timber.w("DownloadRepository: Item $itemId not found in DB for deletion.")
                return@withContext
            }

            itemToDelete.localFileUri?.let { uriString ->
                val fileUri = uriString.toUri()
                var fileDeleted = false

                // IMPORTANT: Get the file path FIRST, before any deletion attempts
                val filePath = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    Timber.d("DownloadRepository: Getting file path for API 28 fallback...")
                    getPathFromUri(fileUri).also { path ->
                        Timber.d("DownloadRepository: Retrieved path: $path")
                    }
                } else null

                // 1. First try MediaStore deletion
                try {
                    val deletedRows = context.contentResolver.delete(fileUri, null, null)
                    if (deletedRows > 0) {
                        Timber.i("DownloadRepository: Successfully deleted MediaStore entry for URI: $uriString")
                        fileDeleted = true
                    } else {
                        Timber.w("DownloadRepository: MediaStore deletion returned 0 rows for URI: $uriString")
                    }
                } catch (e: Exception) {
                    Timber.e(
                        e,
                        "DownloadRepository: MediaStore deletion failed for URI: $uriString"
                    )
                }

                // 2. Then try direct file deletion for API 28 and below
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !filePath.isNullOrBlank()) {
                    try {
                        val file = File(filePath)
                        if (file.exists()) {
                            if (file.delete()) {
                                Timber.i("DownloadRepository: (API 28) Successfully deleted file at: $filePath")
                                fileDeleted = true
                            } else {
                                Timber.e("DownloadRepository: (API 28) Failed to delete file at: $filePath")
                            }
                        } else {
                            Timber.w("DownloadRepository: (API 28) File doesn't exist at: $filePath")
                        }
                    } catch (e: Exception) {
                        Timber.e(
                            e,
                            "DownloadRepository: (API 28) Error during file deletion at: $filePath"
                        )
                    }
                }

                if (!fileDeleted) {
                    Timber.w("DownloadRepository: Failed to confirm file deletion for: $itemId")
                }
            } ?: run {
                Timber.w("DownloadRepository: No localFileUri found for item: $itemId")
            }

            // Always clean up the app state, even if file deletion failed
            try {
                DownloadService.sendRemoveDownload(
                    context,
                    HolodexDownloadService::class.java,
                    itemId,
                    false
                )
                Timber.d("DownloadRepository: Sent remove download command")
            } catch (e: Exception) {
                Timber.e(e, "DownloadRepository: Failed to send remove download command")
            }

            try {
                downloadCache.removeResource(itemId)
                Timber.d("DownloadRepository: Removed from download cache")
            } catch (e: Exception) {
                Timber.e(e, "DownloadRepository: Failed to remove from download cache")
            }

            try {
                downloadedItemDao.deleteById(itemId)
                Timber.d("DownloadRepository: Deleted from database")
            } catch (e: Exception) {
                Timber.e(e, "DownloadRepository: Failed to delete from database")
            }

            Timber.i("DownloadRepository: Deletion process complete for item: $itemId")
        }
    }

    override suspend fun postDownloadCompletedEvent(event: DownloadCompletedEvent) {
        _downloadCompletedEvents.emit(event)
    }

    private fun getPathFromUri(uri: Uri): String? {
        var path: String? = null
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    path = cursor.getString(columnIndex)
                    Timber.d("DownloadRepository: Successfully retrieved path from URI: $path")
                } else {
                    Timber.w("DownloadRepository: Cursor empty for URI: $uri")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "DownloadRepository: Failed to get path from URI: $uri")
        }
        return path
    }

    override suspend fun retryExportForItem(item: DownloadedItemEntity) {
        if (item.downloadStatus != DownloadStatus.EXPORT_FAILED) {
            Timber.w("Cannot retry export for ${item.videoId}, status is not EXPORT_FAILED.")
            return
        }

        Timber.i("Retrying export for ${item.videoId}")

        // This simulates the logic from DownloadCompletionObserver to re-enqueue the worker
        val parentVideoId = item.videoId.split('_').firstOrNull()
        val parentMetadata = parentVideoId?.let { parentVideoMetadataDao.getById(it) }

        if (parentMetadata == null) {
            Timber.e("Cannot retry export for ${item.videoId}, parent metadata not found.")
            downloadedItemDao.updateStatus(
                item.videoId,
                DownloadStatus.FAILED
            ) // Mark as permanently failed
            return
        }

        val albumName = parentMetadata.title
        val startTimeSeconds = item.videoId.split('_').getOrNull(1)?.toLongOrNull() ?: 0L
        val clipStartMs = startTimeSeconds * 1000
        val clipEndMs = (startTimeSeconds + item.durationSec) * 1000

        // Re-use the cache URI which should still be valid
        val cacheUri = "cache://${item.videoId}"

        val workData = Data.Builder()
            .putString(M4AExportWorker.KEY_ITEM_ID, item.videoId)
            .putString(M4AExportWorker.KEY_ORIGINAL_URI, cacheUri)
            .putString(M4AExportWorker.KEY_SONG_TITLE, item.title)
            .putString(M4AExportWorker.KEY_ARTIST_NAME, item.artistText)
            .putString(M4AExportWorker.KEY_ALBUM_NAME, albumName)
            .putString(M4AExportWorker.KEY_ARTWORK_URI, item.artworkUrl)
            .putLong(M4AExportWorker.KEY_CLIP_START_MS, clipStartMs)
            .putLong(M4AExportWorker.KEY_CLIP_END_MS, clipEndMs)
            .apply {
                item.trackNumber?.let { putInt(M4AExportWorker.KEY_TRACK_NUMBER, it) }
            }
            .build()

        val exportRequest = OneTimeWorkRequestBuilder<M4AExportWorker>()
            .setInputData(workData)
            .build()

        workManager.enqueueUniqueWork(
            "export_${item.videoId}",
            ExistingWorkPolicy.REPLACE,
            exportRequest
        )
        downloadedItemDao.updateStatus(
            item.videoId,
            DownloadStatus.PROCESSING
        ) // Set status back to processing
    }

    override suspend fun reconcileAllDownloads() {
        withContext(Dispatchers.IO) {
            Timber.d("DownloadRepository: Starting full download reconciliation.")
            val appDbDownloads = downloadedItemDao.getAllDownloads().first()
            val media3ActiveDownloads = media3DownloadManager.currentDownloads
            val media3ActiveDownloadIds = media3ActiveDownloads.map { it.request.id }.toSet()

            for (item in appDbDownloads) {
                // Reconcile items stuck in active states
                if (item.downloadStatus == DownloadStatus.DOWNLOADING || item.downloadStatus == DownloadStatus.ENQUEUED) {
                    if (!media3ActiveDownloadIds.contains(item.videoId)) {
                        Timber.w("Reconcile: Item ${item.videoId} is stuck in an active state. Marking as FAILED.")
                        downloadedItemDao.updateStatus(item.videoId, DownloadStatus.FAILED)
                    } else {
                        val media3Download =
                            media3ActiveDownloads.find { it.request.id == item.videoId }
                        if (media3Download?.state == Download.STATE_FAILED) {
                            Timber.w("Reconcile: Item ${item.videoId} is FAILED in Media3. Syncing DB.")
                            downloadedItemDao.updateStatus(item.videoId, DownloadStatus.FAILED)
                        }
                    }
                }

                // Reconcile completed items with missing files
                if (item.downloadStatus == DownloadStatus.COMPLETED) {
                    val uriString = item.localFileUri
                    if (uriString.isNullOrBlank()) {
                        Timber.w("Reconcile: Item ${item.videoId} is COMPLETED but has no URI. Deleting record.")
                        deleteDownloadById(item.videoId)
                        continue
                    }
                    val fileExists = try {
                        context.contentResolver.openAssetFileDescriptor(uriString.toUri(), "r")
                            ?.use { true } ?: false
                    } catch (_: Exception) {
                        false
                    }

                    if (!fileExists) {
                        Timber.w("Reconcile: File for completed item ${item.videoId} is missing. Deleting record.")
                        deleteDownloadById(item.videoId)
                    }
                }
            }
            Timber.i("DownloadRepository: Reconciliation complete.")
        }
    }

    override fun getAllDownloads(): Flow<List<DownloadedItemEntity>> =
        downloadedItemDao.getAllDownloads()

    override fun getDownloadById(itemId: String): Flow<DownloadedItemEntity?> =
        downloadedItemDao.getDownloadByIdFlow(itemId)

    override suspend fun rescanStorageForDownloads() {
        withContext(Dispatchers.IO) {
            Timber.d("$TAG: Starting storage re-scan for orphaned downloads.")
            val musicDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val appMusicDir = File(musicDir, "HolodexMusic")

            if (!appMusicDir.exists() || !appMusicDir.isDirectory) {
                Timber.d("$TAG: HolodexMusic directory does not exist. No scan needed.")
                return@withContext
            }

            val mediaFiles =
                appMusicDir.listFiles { _, name -> name.endsWith(".m4a") } ?: return@withContext
            if (mediaFiles.isEmpty()) {
                Timber.d("$TAG: No .m4a files found in HolodexMusic directory.")
                return@withContext
            }

            Timber.d("$TAG: Found ${mediaFiles.size} potential files to scan.")
            val allDbDownloads = downloadedItemDao.getAllDownloads().first()
            val dbIds = allDbDownloads.map { it.videoId }.toSet()

            var importedCount = 0
            for (file in mediaFiles) {
                try {
                    val audioFile = AudioFileIO.read(file)
                    val tag = audioFile.tag
                    val comment = tag?.getFirst(FieldKey.COMMENT)

                    if (comment != null && comment.startsWith("holodex_item_id::")) {
                        val itemId = comment.substringAfter("holodex_item_id::")

                        if (!dbIds.contains(itemId)) {
                            // This is an orphaned file we need to re-import
                            val title = tag.getFirst(FieldKey.TITLE)
                            val artist = tag.getFirst(FieldKey.ARTIST)
                            val album = tag.getFirst(FieldKey.ALBUM)
                            val trackNum = tag.getFirst(FieldKey.TRACK)?.toIntOrNull()

                            val parentVideoId = itemId.split('_').first()
                            val songStartSec = itemId.split('_').getOrNull(1)?.toLongOrNull() ?: 0L
                            val durationSec = (audioFile.audioHeader.trackLength).toLong()

                            // Reconstruct the entity from the metadata
                            val entity = DownloadedItemEntity(
                                videoId = itemId,
                                title = title.ifEmpty { "Unknown Title" },
                                artistText = artist.ifEmpty { "Unknown Artist" },
                                channelId = "", // This data is lost, but not critical
                                artworkUrl = null, // This data is lost
                                durationSec = durationSec,
                                localFileUri = Uri.fromFile(file).toString(),
                                downloadStatus = DownloadStatus.COMPLETED,
                                downloadedAt = file.lastModified(),
                                fileName = file.name,
                                targetFormat = "M4A",
                                downloadId = null,
                                progress = 100,
                                trackNumber = trackNum
                            )
                            downloadedItemDao.insertOrUpdate(entity)
                            importedCount++
                            Timber.i("$TAG: Re-imported orphaned file: ${file.name} (ID: $itemId)")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to read metadata for file: ${file.name}")
                }
            }
            if (importedCount > 0) {
                Timber.i("$TAG: Successfully re-imported $importedCount orphaned downloads.")
            } else {
                Timber.d("$TAG: Re-scan finished. No new orphaned files found.")
            }
        }
    }
}
