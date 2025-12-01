package com.example.holodex

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.annotation.ExperimentalCoilApi
import com.example.holodex.data.download.DownloadCompletionObserver
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.di.ApplicationScope
import com.example.holodex.extractor.DownloaderImpl
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
class MyApp : Application(), ImageLoaderFactory, DefaultLifecycleObserver, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var holodexRepository: HolodexRepository

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var downloadCompletionObserver: DownloadCompletionObserver

    @Inject
    lateinit var imageLoader: ImageLoader

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
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannels()
        downloadCompletionObserver.initialize()

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

    @UnstableApi
    override fun onStart(owner: LifecycleOwner) {
        Timber.i("MyApp entering foreground. Triggering reconciliations.")
        appScope.launch {
            downloadRepository.reconcileAllDownloads()
            downloadRepository.rescanStorageForDownloads()
        }
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val downloadChannel = NotificationChannel(
            "download_channel",
            getString(R.string.download_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.download_notification_channel_description)
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
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader // Use the Hilt-provided singleton
    }

    @OptIn(ExperimentalCoilApi::class)
    @UnstableApi
    fun clearAllAppCachesOnDemand(callback: (Boolean) -> Unit) {
        appScope.launch {
            var allSuccess = true
            try {
                withContext(Dispatchers.IO) {
                    val mediaCacheDir = File(applicationContext.cacheDir, "exoplayer_media_cache")
                    val downloadCacheDir = File(applicationContext.getExternalFilesDir(null), "downloads")
                    mediaCacheDir.deleteRecursively()
                    downloadCacheDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting ExoPlayer caches.")
                allSuccess = false
            }

            try {
                imageLoader.diskCache?.clear()
                imageLoader.memoryCache?.clear()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing Coil cache.")
                allSuccess = false
            }

            try {
                holodexRepository.clearAllCachedData()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing repository data.")
                allSuccess = false
            }

            callback(allSuccess)

            withContext(Dispatchers.Main) {
                delay(500)
                val packageManager = applicationContext.packageManager
                val intent = packageManager.getLaunchIntentForPackage(applicationContext.packageName)
                val componentName = intent!!.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                applicationContext.startActivity(mainIntent)
                exitProcess(0)
            }
        }
    }
}