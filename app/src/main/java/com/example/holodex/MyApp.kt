package com.example.holodex

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.DownloadedItemDao
import com.example.holodex.data.download.DownloadCompletionObserver
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.di.ApplicationScope
import com.example.holodex.extractor.DownloaderImpl
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.system.exitProcess

@UnstableApi
@HiltAndroidApp
// --- FIX: Implement Configuration.Provider ---
class MyApp : Application(), ImageLoaderFactory, DefaultLifecycleObserver, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var holodexRepository: HolodexRepository

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var downloadManager: androidx.media3.exoplayer.offline.DownloadManager

    @Inject
    lateinit var downloadedItemDao: DownloadedItemDao

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var downloadCompletionObserver: DownloadCompletionObserver


    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()



    override fun onCreate() {
        super<Application>.onCreate()


        Timber.i("✅ WorkManager has been explicitly initialized with HiltWorkerFactory.")

        // The rest of the startup sequence can now proceed safely.
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannels()

        Timber.d("Initializing DownloadCompletionObserver...")
        downloadCompletionObserver.initialize()
        Timber.d("DownloadCompletionObserver initialized.")

        appScope.launch {
            holodexRepository.cleanupExpiredCacheEntries()
        }

        val downloaderOkHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        NewPipe.init(
            DownloaderImpl(downloaderOkHttpClient),
            Localization.fromLocale(Locale.JAPAN),
            ContentCountry(Locale.JAPAN.country)
        )
    }

    // The rest of your MyApp.kt file is unchanged.
    // The following methods are included for completeness but require no changes.
    @UnstableApi
    override fun onStart(owner: LifecycleOwner) {
        Timber.i("MyApp entering foreground (onStart lifecycle event). Triggering all reconciliations.")
        appScope.launch {
            reconcileActiveDownloadStates()
            downloadRepository.rescanStorageForDownloads()
            downloadRepository.reconcileAllDownloads()
        }
    }


    @UnstableApi
    private fun reconcileActiveDownloadStates() {
        appScope.launch {
            try {
                Timber.d("MyApp Reconcile: Starting ACTIVE download state reconciliation.")

                val appDbDownloads = downloadedItemDao.getAllDownloads().first()
                val media3ActiveDownloads = downloadManager.currentDownloads
                val media3ActiveDownloadIds = media3ActiveDownloads.map { it.request.id }.toSet()

                for (appDbItem in appDbDownloads) {
                    if (appDbItem.downloadStatus == DownloadStatus.DOWNLOADING || appDbItem.downloadStatus == DownloadStatus.ENQUEUED) {
                        if (!media3ActiveDownloadIds.contains(appDbItem.videoId)) {
                            Timber.w("MyApp Reconcile: Item ${appDbItem.videoId} is stuck in an active state but not known to DownloadManager. Marking as FAILED.")
                            downloadedItemDao.updateStatus(appDbItem.videoId, DownloadStatus.FAILED)
                        } else {
                            val media3Download =
                                media3ActiveDownloads.find { it.request.id == appDbItem.videoId }

                            if (media3Download?.state == androidx.media3.exoplayer.offline.Download.STATE_FAILED) {
                                Timber.w("MyApp Reconcile: Item ${appDbItem.videoId} is FAILED in Media3. Syncing our DB to FAILED.")
                                downloadedItemDao.updateStatus(
                                    appDbItem.videoId,
                                    DownloadStatus.FAILED
                                )
                            }
                        }
                    }
                }
                Timber.d("MyApp Reconcile: Active download state reconciliation finished.")
            } catch (e: Exception) {
                Timber.e(e, "MyApp Reconcile: Error during ACTIVE download state reconciliation.")
            }
        }
    }


    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val downloadChannel = NotificationChannel(
            "download_channel",
            getString(R.string.download_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                getString(R.string.download_notification_channel_description)
        }

        val playbackChannel = NotificationChannel(
            "holodex_playback_channel_v3",
            getString(R.string.playback_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.playback_notification_channel_description)
        }

        notificationManager.createNotificationChannel(downloadChannel)
        notificationManager.createNotificationChannel(playbackChannel)
        Timber.d("Notification channels created: 'download_channel' and 'holodex_playback_channel_v3'.")
    }


    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache_v1"))
                    .maxSizeBytes(50L * 1024L * 1024L) // 50MB
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .build()
            }
            .respectCacheHeaders(false)
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()
    }


    internal fun reconcileCompletedDownloads() {
        appScope.launch {
            try {
                Timber.d("MyApp Reconcile: Verifying file existence for completed downloads...")
                val completedDownloads = downloadedItemDao.getAllDownloads()
                    .first()
                    .filter { it.downloadStatus == DownloadStatus.COMPLETED }

                for (item in completedDownloads) {
                    var fileExists = false
                    val uriString = item.localFileUri

                    if (!uriString.isNullOrBlank()) {
                        try {
                            contentResolver.openInputStream(uriString.toUri())?.use {
                                fileExists = true
                            }
                        } catch (_: Exception) {
                            fileExists = false
                        }
                    }

                    if (!fileExists) {
                        Timber.w("MyApp Reconcile: File for item ${item.videoId} is missing. Triggering robust delete to clean up stale DB entry.")
                        downloadRepository.deleteDownloadById(item.videoId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "MyApp Reconcile: CRITICAL error during completed download reconciliation."
                )
            }
        }
    }


    @OptIn(ExperimentalCoilApi::class)
    @UnstableApi
    fun clearAllAppCachesOnDemand(callback: (Boolean) -> Unit) {
        appScope.launch {
            var allSuccess = true

            try {
                withContext(Dispatchers.IO) {
                    val mediaCacheDir = File(applicationContext.cacheDir, "exoplayer_media_cache")
                    val downloadCacheDir =
                        File(applicationContext.getExternalFilesDir(null), "downloads")

                    if (mediaCacheDir.exists()) mediaCacheDir.deleteRecursively()
                    if (downloadCacheDir.exists()) downloadCacheDir.deleteRecursively()

                    Timber.i("Force-deleted ExoPlayer cache directories.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during manual deletion of ExoPlayer caches.")
                allSuccess = false
            }

            try {
                imageLoader.diskCache?.clear()
                imageLoader.memoryCache?.clear()
                Timber.i("Coil image caches cleared.")
            } catch (e: Exception) {
                Timber.e(e, "Error clearing Coil image cache.")
                allSuccess = false
            }

            try {
                holodexRepository.clearAllCachedData()
                Timber.i("Holodex repository data cleared.")
            } catch (e: Exception) {
                Timber.e(e, "Error clearing Holodex repository data.")
                allSuccess = false
            }

            Timber.i("Application caches clear attempt finished. Success: $allSuccess")
            callback(allSuccess)

            withContext(Dispatchers.Main) {
                delay(500)
                val packageManager = applicationContext.packageManager
                val intent =
                    packageManager.getLaunchIntentForPackage(applicationContext.packageName)
                val componentName = intent!!.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                applicationContext.startActivity(mainIntent)
                exitProcess(0)
            }
        }
    }
}