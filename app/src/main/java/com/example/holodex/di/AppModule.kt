// File: java\com\example\holodex\di\AppModule.kt
package com.example.holodex.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.work.WorkManager
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.holodex.util.PaletteExtractor
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(app: Application): SharedPreferences {
        return app.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()



    @Provides
    @Singleton
    @ApplicationScope // We'll create this annotation for clarity
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache_v1"))
                    .maxSizeBytes(50L * 1024L * 1024L) // Increased to 100MB for better offline support
                    .build()
            }
            .crossfade(true) // Reduces visual jitter on load
            .allowHardware(true) // CRITICAL: Uses GPU for bitmaps, saving Main Thread CPU
            .respectCacheHeaders(false) // Aggressively cache images regardless of server headers
            .build()
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    @Provides
    @Singleton
    @UnstableApi
    fun provideDownloadNotificationHelper(@ApplicationContext context: Context): DownloadNotificationHelper {
        return DownloadNotificationHelper(context, "download_channel")
    }
    @Provides
    @Singleton
    fun providePaletteExtractor(@ApplicationContext context: Context): PaletteExtractor {
        return PaletteExtractor(context)
    }
}