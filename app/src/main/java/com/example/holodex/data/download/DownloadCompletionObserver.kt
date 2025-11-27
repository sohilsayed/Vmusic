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
import com.example.holodex.data.db.UnifiedDao
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
    private val unifiedDao: UnifiedDao // <--- UPDATED: Uses UnifiedDao
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

            // 1. Check Unified DB
            val currentEntry = unifiedDao.getDownloadInteraction(itemId)
            if (currentEntry == null) {
                Timber.w("$TAG: onDownloadChanged for ID: $itemId, but no DB entry found! Ignoring.")
                return@launch
            }

            // 2. Get Metadata for Export logic
            val projection = unifiedDao.getItemByIdOneShot(itemId) ?: return@launch

            when (download.state) {
                Download.STATE_COMPLETED -> {
                    Timber.i("$TAG: Media3 download COMPLETED for ID: $itemId. Enqueueing export worker.")
                    unifiedDao.updateDownloadStatus(itemId, DownloadStatus.PROCESSING.name)

                    val clipStartMs = (projection.metadata.startSeconds ?: 0) * 1000L
                    val clipEndMs = (projection.metadata.endSeconds ?: 0) * 1000L

                    val workData = Data.Builder()
                        .putString(M4AExportWorker.KEY_ITEM_ID, itemId)
                        .putString(M4AExportWorker.KEY_ORIGINAL_URI, download.request.uri.toString())
                        .putString(M4AExportWorker.KEY_SONG_TITLE, projection.metadata.title)
                        .putString(M4AExportWorker.KEY_ARTIST_NAME, projection.metadata.artistName)
                        .putString(M4AExportWorker.KEY_ALBUM_NAME, projection.metadata.title) // Or parent title if available
                        .putString(M4AExportWorker.KEY_ARTWORK_URI, projection.metadata.specificArtUrl)
                        .putLong(M4AExportWorker.KEY_CLIP_START_MS, clipStartMs)
                        .putLong(M4AExportWorker.KEY_CLIP_END_MS, clipEndMs)
                        .apply {
                            currentEntry.downloadTrackNum?.let { putInt(M4AExportWorker.KEY_TRACK_NUMBER, it) }
                        }
                        .build()

                    val exportRequest = OneTimeWorkRequestBuilder<M4AExportWorker>()
                        .setInputData(workData)
                        .build()

                    workManager.enqueueUniqueWork("export_$itemId", ExistingWorkPolicy.REPLACE, exportRequest)
                }
                Download.STATE_FAILED -> {
                    Timber.e(finalException, "$TAG: Download FAILED for ID: $itemId.")
                    unifiedDao.updateDownloadStatus(itemId, DownloadStatus.FAILED.name)
                }
                Download.STATE_STOPPED -> {
                    if (download.stopReason != 0 || finalException != null) {
                        unifiedDao.updateDownloadStatus(itemId, DownloadStatus.FAILED.name)
                    }
                }
                Download.STATE_RESTARTING -> {
                    if (currentEntry.downloadStatus != DownloadStatus.COMPLETED.name) {
                        unifiedDao.updateDownloadStatus(itemId, DownloadStatus.DOWNLOADING.name)
                    }
                }
                else -> { /* Other states like QUEUED/DOWNLOADING handled by initial insert */ }
            }
        }
    }
}