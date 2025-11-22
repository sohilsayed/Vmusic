package com.example.holodex.data.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.holodex.background.M4AExportWorker
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.DownloadedItemDao
import com.example.holodex.data.db.ParentVideoMetadataDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class DownloadCompletionObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val downloadedItemDao: DownloadedItemDao,
    private val parentVideoMetadataDao: ParentVideoMetadataDao
) : DownloadManager.Listener {

    companion object {
        private const val TAG = "DownloadCompletionObs"
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val workManager = WorkManager.getInstance(context)

    fun initialize() {
        downloadManager.addListener(this)
    }

    override fun onDownloadChanged(
        manager: DownloadManager,
        download: Download,
        finalException: Exception?
    ) {
        scope.launch {
            val itemId = download.request.id

            // --- FIX: Perform null check at the top and return early ---
            val currentDbEntry = downloadedItemDao.getById(itemId)
            if (currentDbEntry == null) {
                Timber.w("$TAG: onDownloadChanged for ID: $itemId, but no corresponding DB entry was found! Ignoring event.")
                return@launch
            }
            // --- END OF FIX ---

            // From this point on, the compiler knows `currentDbEntry` is not null.

            when (download.state) {
                Download.STATE_COMPLETED -> {
                    Timber.i("$TAG: Media3 download COMPLETED for ID: $itemId. Target format: ${currentDbEntry.targetFormat}. Enqueueing export worker.")
                    downloadedItemDao.updateStatus(itemId, DownloadStatus.PROCESSING)

                    val parentVideoId = itemId.split('_').firstOrNull()
                    val parentMetadata = parentVideoId?.let { parentVideoMetadataDao.getById(it) }

                    if (parentMetadata == null) {
                        Timber.e("$TAG: Parent metadata not found for $itemId. Cannot proceed with export.")
                        downloadedItemDao.updateStatus(itemId, DownloadStatus.FAILED)
                        return@launch
                    }

                    val albumName = parentMetadata.title
                    val startTimeSeconds = currentDbEntry.videoId.split('_').getOrNull(1)?.toLongOrNull() ?: 0L
                    val clipStartMs = startTimeSeconds * 1000
                    val clipEndMs = (startTimeSeconds + currentDbEntry.durationSec) * 1000
                    val trackNumber = currentDbEntry.trackNumber

                    when (currentDbEntry.targetFormat) {
                        "M4A", "" -> {
                            val workData = Data.Builder()
                                .putString(M4AExportWorker.KEY_ITEM_ID, itemId)
                                .putString(M4AExportWorker.KEY_ORIGINAL_URI, download.request.uri.toString())
                                .putString(M4AExportWorker.KEY_SONG_TITLE, currentDbEntry.title)
                                .putString(M4AExportWorker.KEY_ARTIST_NAME, currentDbEntry.artistText)
                                .putString(M4AExportWorker.KEY_ALBUM_NAME, albumName)
                                .putString(M4AExportWorker.KEY_ARTWORK_URI, currentDbEntry.artworkUrl)
                                .putLong(M4AExportWorker.KEY_CLIP_START_MS, clipStartMs)
                                .putLong(M4AExportWorker.KEY_CLIP_END_MS, clipEndMs)
                                .apply { trackNumber?.let { putInt(M4AExportWorker.KEY_TRACK_NUMBER, it) } }
                                .build()

                            val exportRequest = OneTimeWorkRequestBuilder<M4AExportWorker>()
                                .setInputData(workData)
                                .build()

                            workManager.enqueueUniqueWork("export_$itemId", ExistingWorkPolicy.REPLACE, exportRequest)
                            Timber.d("$TAG: Enqueued M4AExportWorker for $itemId.")
                        }
                        else -> {
                            Timber.e("$TAG: Unknown target format '${currentDbEntry.targetFormat}' for item $itemId. Failing download.")
                            downloadedItemDao.updateStatus(itemId, DownloadStatus.FAILED)
                        }
                    }
                }
                Download.STATE_FAILED -> {
                    Timber.e(finalException, "$TAG: Media3 download FAILED for ID: $itemId. Reason Code: ${download.stopReason}")
                    downloadedItemDao.updateStatus(itemId, DownloadStatus.FAILED)
                }
                Download.STATE_STOPPED -> {
                    Timber.w("$TAG: Download STOPPED for ID: $itemId. Reason: ${download.stopReason}. Final Exception: ${finalException?.message}")
                    // The C.LENGTH_UNSET is a Long, download.stopReason is Int. These should not be compared directly.
                    // The original comparison was `if (download.stopReason != Download.STOP_REASON_NONE || finalException != null)`
                    // Download.STOP_REASON_NONE is an Int (0). This comparison is type-correct.
                    if (download.stopReason != Download.STOP_REASON_NONE || finalException != null) {
                        downloadedItemDao.updateStatus(itemId, DownloadStatus.FAILED)
                    } else {
                        if(currentDbEntry?.downloadStatus == DownloadStatus.DOWNLOADING) {
                            // No specific action, let reconciliation handle if it doesn't restart
                        }
                        Timber.d("$TAG: Download $itemId STOPPED without error, current DB status: ${currentDbEntry?.downloadStatus}")
                    }
                }
                Download.STATE_REMOVING -> {
                    Timber.d("$TAG: Media3 download is being removed (ID: ${download.request.id}). The repository is responsible for final cleanup.")
                }
                else -> {
                    Timber.d("$TAG: Download state ${download.state} (may be RESTARTING) for ID: $itemId.")
                    if (download.state == Download.STATE_RESTARTING && currentDbEntry?.downloadStatus != DownloadStatus.COMPLETED) {
                        downloadedItemDao.updateStatus(itemId, DownloadStatus.DOWNLOADING)
                    }
                }
            }
        }
    }
}