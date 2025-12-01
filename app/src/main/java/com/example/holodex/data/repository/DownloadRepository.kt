package com.example.holodex.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.holodex.background.M4AExportWorker
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.db.UserInteractionEntity
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.di.ApplicationScope
import com.example.holodex.di.DownloadCache
import com.example.holodex.di.UpstreamDataSource
import com.example.holodex.playback.data.source.StreamResolutionCoordinator
import com.example.holodex.service.HolodexDownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    suspend fun resumeDownload(itemId: String)
    suspend fun retryExport(itemId: String)
    suspend fun reconcileAllDownloads()
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
    private val unifiedDao: UnifiedDao,
    private val youtubeStreamRepository: YouTubeStreamRepository,
    @DownloadCache private val downloadCache: SimpleCache,
    @UpstreamDataSource private val upstreamDataSourceFactory: DataSource.Factory,
    private val media3DownloadManager: DownloadManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val workManager: WorkManager,
    private val streamResolutionCoordinator: StreamResolutionCoordinator
) : DownloadRepository {

    companion object {
        private const val TAG = "DownloadRepositoryImpl"
        private const val DOWNLOAD_FOLDER_NAME = "HolodexMusic"
    }

    private val _downloadCompletedEvents = MutableSharedFlow<DownloadRepository.DownloadCompletedEvent>()
    override val downloadCompletedEvents: SharedFlow<DownloadRepository.DownloadCompletedEvent> =
        _downloadCompletedEvents.asSharedFlow()

    override suspend fun startDownload(video: HolodexVideoItem, song: HolodexSong) {
        val itemId = "${video.id}_${song.start}"
        val displayTitle = song.name.ifBlank { video.title }
        val durationSec = (song.end - song.start).toLong()

        val existing = unifiedDao.getDownloadInteraction(itemId)
        if (existing?.downloadStatus in listOf(DownloadStatus.ENQUEUED.name, DownloadStatus.DOWNLOADING.name, DownloadStatus.COMPLETED.name, DownloadStatus.PROCESSING.name)) {
            Timber.w("$TAG: Download for $itemId is already active/done. Skipping.")
            return
        }

        Timber.d("$TAG: Initiating download for: $itemId")

        applicationScope.launch(Dispatchers.IO) {
            var downloadHelper: DownloadHelper? = null
            try {
                // 1. Upsert Metadata & Interaction State (Existing code)
                val metadata = UnifiedMetadataEntity(
                    id = itemId, title = displayTitle, artistName = video.channel.name, type = "SEGMENT",
                    specificArtUrl = song.artUrl, uploaderAvatarUrl = video.channel.photoUrl, duration = durationSec,
                    channelId = video.channel.id ?: "unknown", parentVideoId = video.id,
                    startSeconds = song.start.toLong(), endSeconds = song.end.toLong(),
                    lastUpdatedAt = System.currentTimeMillis()
                )
                unifiedDao.upsertMetadata(metadata)

                val interaction = UserInteractionEntity(
                    itemId = itemId, interactionType = "DOWNLOAD", timestamp = System.currentTimeMillis(),
                    downloadStatus = DownloadStatus.ENQUEUED.name, downloadTargetFormat = "M4A",
                    downloadProgress = 0, downloadFileName = "${displayTitle.take(50)}.m4a"
                )
                unifiedDao.upsertInteraction(interaction)

                // 2. Resolve Stream URL (Existing code)
                val streamUrl = streamResolutionCoordinator.getCachedUrl(video.id) ?: withTimeout(30_000) {
                    // Pass true here to force M4A selection
                    youtubeStreamRepository.getAudioStreamDetails(video.id, preferM4a = true).getOrThrow().streamUrl
                }




                // 4. Create MediaItem WITH ClippingConfiguration (*** THE FIX ***)
                val mediaItem = MediaItem.fromUri(streamUrl)

                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(downloadCache)
                    .setUpstreamDataSourceFactory(upstreamDataSourceFactory)

                // 4. Create Helper
                val downloadHelperFactory = DownloadHelper.Factory().setDataSourceFactory(cacheDataSourceFactory)
                downloadHelper = downloadHelperFactory.create(mediaItem)


                // 6. Prepare and Get Request
                val request = suspendCancellableCoroutine<DownloadRequest> { continuation ->
                    downloadHelper.prepare(object : DownloadHelper.Callback {
                        override fun onPrepared(
                            helper: DownloadHelper,
                            tracksInfoAvailable: Boolean
                        ) {
                            try {
                                // Calculate milliseconds
                                val startMs = song.start * 1000L
                                val durationMs = (song.end - song.start) * 1000L

                                // Use the overload explicitly designed for progressive streams.
                                // This forces the helper to calculate the byte range for this time window.
                                val req = helper.getDownloadRequest(
                                    itemId,                                     // id
                                    displayTitle.toByteArray(StandardCharsets.UTF_8), // data
                                    startMs,                                    // startPositionMs
                                    durationMs                                  // durationMs
                                )
                                continuation.resume(req)
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        }

                        override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                            continuation.resumeWithException(e)
                        }
                    })
                    continuation.invokeOnCancellation {
                        downloadHelper.release()
                    }
                }

                // 6. Dispatch to Service
                DownloadService.sendAddDownload(context, HolodexDownloadService::class.java, request, true)
                Timber.i("$TAG: Download dispatched with Partial Range: ${song.start}s to ${song.end}s")

            } catch (e: Exception) {
                Timber.e(e, "Download setup failed for $itemId")
                unifiedDao.updateDownloadStatus(itemId, DownloadStatus.FAILED.name)
            } finally {
                downloadHelper?.release()
            }
        }
    }

    override suspend fun retryExport(itemId: String) {
        val projection = unifiedDao.getItemByIdOneShot(itemId) ?: return
        val downloadInt = projection.interactions.find { it.interactionType == "DOWNLOAD" } ?: return
        if (downloadInt.downloadStatus != DownloadStatus.EXPORT_FAILED.name) return

        Timber.i("Retrying export for $itemId")

        val workData = Data.Builder()
            .putString(M4AExportWorker.KEY_ITEM_ID, itemId)
            .putString(M4AExportWorker.KEY_ORIGINAL_URI, "cache://$itemId")
            .putString(M4AExportWorker.KEY_SONG_TITLE, projection.metadata.title)
            .putString(M4AExportWorker.KEY_ARTIST_NAME, projection.metadata.artistName)
            .putLong(M4AExportWorker.KEY_CLIP_START_MS, 0) // Clipping is already handled by the downloaded segment
            .putLong(M4AExportWorker.KEY_CLIP_END_MS, projection.metadata.duration * 1000L)
            .build()

        val exportRequest = OneTimeWorkRequestBuilder<M4AExportWorker>().setInputData(workData).build()
        workManager.enqueueUniqueWork("export_$itemId", ExistingWorkPolicy.REPLACE, exportRequest)
        unifiedDao.updateDownloadStatus(itemId, DownloadStatus.PROCESSING.name)
    }

    override suspend fun deleteDownloadById(itemId: String) {
        unifiedDao.deleteInteraction(itemId, "DOWNLOAD")
        downloadCache.removeResource(itemId)
        DownloadService.sendRemoveDownload(context, HolodexDownloadService::class.java, itemId, false)
    }

    override suspend fun cancelDownload(itemId: String) {
        DownloadService.sendRemoveDownload(context, HolodexDownloadService::class.java, itemId, false)
        unifiedDao.deleteInteraction(itemId, "DOWNLOAD")
    }

    override suspend fun resumeDownload(itemId: String) {
        DownloadService.sendResumeDownloads(context, HolodexDownloadService::class.java, true)
        unifiedDao.updateDownloadStatus(itemId, DownloadStatus.ENQUEUED.name)
    }

    override suspend fun reconcileAllDownloads() {
        withContext(Dispatchers.IO) {
            Timber.i("Reconciling downloads: Checking for zombie states...")

            // 1. Get all items the DB thinks are downloading
            val activeInDb = unifiedDao.getAllDownloadsOneShot().filter {
                it.downloadStatus == DownloadStatus.DOWNLOADING.name ||
                        it.downloadStatus == DownloadStatus.ENQUEUED.name ||
                        it.downloadStatus == DownloadStatus.PROCESSING.name
            }

            // 2. Get what Media3 actually knows about
            val actuallyRunning = media3DownloadManager.currentDownloads.map { it.request.id }.toSet()

            var fixedCount = 0
            for (item in activeInDb) {
                // If DB says downloading, but Media3 doesn't know about it, it's a zombie.
                if (!actuallyRunning.contains(item.itemId)) {
                    Timber.w("Found zombie download: ${item.itemId}. Marking as FAILED.")
                    unifiedDao.updateDownloadStatus(item.itemId, DownloadStatus.FAILED.name)
                    fixedCount++
                }
            }

            if (fixedCount > 0) {
                Timber.i("Reconciliation complete. Fixed $fixedCount zombie downloads.")
            }
        }
    }


    override suspend fun rescanStorageForDownloads() {
        withContext(Dispatchers.IO) {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val appMusicDir = File(musicDir, DOWNLOAD_FOLDER_NAME)
            if (!appMusicDir.exists() || !appMusicDir.isDirectory) return@withContext
            val mediaFiles = appMusicDir.listFiles { _, name -> name.endsWith(".m4a") } ?: return@withContext
            val existingIds = unifiedDao.getAllDownloadsOneShot().map { it.itemId }.toSet()
            for (file in mediaFiles) {
                try {
                    val audioFile = AudioFileIO.read(file)
                    val comment = audioFile.tag?.getFirst(FieldKey.COMMENT)
                    if (comment != null && comment.startsWith("holodex_item_id::")) {
                        val itemId = comment.substringAfter("holodex_item_id::")
                        if (!existingIds.contains(itemId)) {
                            val title = audioFile.tag?.getFirst(FieldKey.TITLE) ?: "Unknown"
                            val artist = audioFile.tag?.getFirst(FieldKey.ARTIST) ?: "Unknown"
                            val duration = audioFile.audioHeader.trackLength.toLong()
                            val parentId = itemId.split("_").firstOrNull() ?: itemId
                            val start = itemId.split("_").getOrNull(1)?.toLongOrNull() ?: 0L

                            val meta = UnifiedMetadataEntity(
                                id = itemId, title = title, artistName = artist, type = "SEGMENT",
                                specificArtUrl = null, uploaderAvatarUrl = null, duration = duration,
                                channelId = "", parentVideoId = parentId, startSeconds = start, endSeconds = start + duration,
                                lastUpdatedAt = System.currentTimeMillis()
                            )
                            unifiedDao.upsertMetadata(meta)

                            val interaction = UserInteractionEntity(
                                itemId = itemId, interactionType = "DOWNLOAD", timestamp = file.lastModified(),
                                localFilePath = Uri.fromFile(file).toString(),
                                downloadStatus = DownloadStatus.COMPLETED.name,
                                downloadFileName = file.name,
                                downloadProgress = 100
                            )
                            unifiedDao.upsertInteraction(interaction)
                        }
                    }
                } catch (e: Exception) { Timber.e(e, "Failed to scan file: ${file.name}") }
            }
        }
    }

    override suspend fun postDownloadCompletedEvent(event: DownloadRepository.DownloadCompletedEvent) {
        _downloadCompletedEvents.emit(event)
    }
}