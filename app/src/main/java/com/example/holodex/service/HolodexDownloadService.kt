package com.example.holodex.service

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.workmanager.WorkManagerScheduler
import com.example.holodex.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class HolodexDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_notification_channel_name,
    R.string.download_notification_channel_description
) {

    // --- Injected fields remain the same ---
    @Inject
    lateinit var downloadManagerInstance: DownloadManager

    @Inject
    lateinit var notificationHelper: DownloadNotificationHelper

    // --- All listener and scope code is REMOVED from here down to onDestroy ---

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 2
        private const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
        private const val DOWNLOAD_WORK_MANAGER_JOB_ID = "holodex_download_job"
    }

    override fun getDownloadManager(): DownloadManager {
        // Just return the injected instance. No need to add/remove listeners.
        return downloadManagerInstance
    }

    override fun getScheduler(): Scheduler {
        return WorkManagerScheduler(this, DOWNLOAD_WORK_MANAGER_JOB_ID)
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return notificationHelper.buildProgressNotification(
            this, R.drawable.ic_notification_small, null, null, downloads, notMetRequirements
        )
    }

    // --- No need for onDestroy to cancel a job anymore ---
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("HolodexDownloadService destroyed.")
    }
}