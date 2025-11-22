// File: java/com/example/holodex/di/PlaybackModule.kt
package com.example.holodex.di

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.example.holodex.data.db.DownloadedItemDao
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.UserPreferencesRepository
import com.example.holodex.playback.data.mapper.MediaItemMapper
import com.example.holodex.playback.data.model.PersistedPlaybackStateDao
import com.example.holodex.playback.data.persistence.PlaybackStatePersistenceManager
import com.example.holodex.playback.data.preload.PreloadConfiguration
import com.example.holodex.playback.data.preload.PreloadStatusController
import com.example.holodex.playback.data.queue.PlaybackQueueManager
import com.example.holodex.playback.data.queue.ShuffleOrderProvider
import com.example.holodex.playback.data.repository.HolodexStreamResolverRepositoryImpl
import com.example.holodex.playback.data.repository.Media3PlaybackRepositoryImpl
import com.example.holodex.playback.data.repository.RoomPlaybackStateRepositoryImpl
import com.example.holodex.playback.data.source.StreamResolutionCoordinator
import com.example.holodex.playback.data.tracker.PlaybackProgressTracker
import com.example.holodex.playback.domain.repository.PlaybackRepository
import com.example.holodex.playback.domain.repository.PlaybackStateRepository
import com.example.holodex.playback.domain.repository.StreamResolverRepository
import com.example.holodex.playback.player.Media3PlayerController
import com.example.holodex.viewmodel.AppPreferenceConstants
import com.example.holodex.viewmodel.autoplay.AutoplayItemProvider
import com.example.holodex.viewmodel.autoplay.ContinuationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Singleton

private data class BufferSettings(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
)

@Module
@InstallIn(SingletonComponent::class)
@UnstableApi
object PlaybackModule {

    @Provides
    @Singleton
    fun provideLoadControl(sharedPreferences: SharedPreferences): DefaultLoadControl {
        val bufferingStrategy = sharedPreferences.getString(
            AppPreferenceConstants.PREF_BUFFERING_STRATEGY,
            AppPreferenceConstants.BUFFERING_STRATEGY_AGGRESSIVE
        ) ?: AppPreferenceConstants.BUFFERING_STRATEGY_AGGRESSIVE

        val settings = when (bufferingStrategy) {
            AppPreferenceConstants.BUFFERING_STRATEGY_BALANCED -> {
                Timber.d("ExoPlayer LoadControl: BALANCED")
                BufferSettings(20000, 60000, 3000, 5000)
            }
            AppPreferenceConstants.BUFFERING_STRATEGY_STABLE -> {
                Timber.d("ExoPlayer LoadControl: STABLE")
                BufferSettings(30000, 120000, 7500, 10000)
            }
            else -> {
                Timber.d("ExoPlayer LoadControl: AGGRESSIVE (default)");
                BufferSettings(10000, 60000, 1000, 2500)
            }
        }

        return DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                settings.minBufferMs,
                settings.maxBufferMs,
                settings.bufferForPlaybackMs,
                settings.bufferForPlaybackAfterRebufferMs
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context, loadControl: DefaultLoadControl, mediaSourceFactory: DefaultMediaSourceFactory): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
    }

    @Provides
    @Singleton
    fun providePlayer(exoPlayer: ExoPlayer): Player = exoPlayer

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        @DownloadCache downloadCache: SimpleCache
    ): DownloadManager {
        val databaseProvider = StandaloneDatabaseProvider(context)
        val downloadManagerDataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent("HolodexAppDownloader/1.0")
        return DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            downloadManagerDataSourceFactory,
            Executors.newFixedThreadPool(3)
        ).apply { resumeDownloads() }
    }

    @Provides
    @Singleton
    fun provideDataSourceFactory(
        @ApplicationContext context: Context,
        @DownloadCache downloadCache: SimpleCache,
        @MediaCache mediaCache: SimpleCache
    ): DataSource.Factory {
        // --- START OF REPLACEMENT ---

        // 1. The base factory for network requests.
        val upstreamFactory = DefaultHttpDataSource.Factory().setUserAgent("HolodexApp/1.0")

        // 2. The default factory that handles most schemes (http, https, content, file, etc.).
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, upstreamFactory)

        // 3. The factory that handles streaming from the media cache.
        val mediaCacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory) // Use the default factory as upstream

        // 4. Our final, all-encompassing factory.
        return object : DataSource.Factory {
            override fun createDataSource(): DataSource {
                // The source for handling downloads via the "cache://" scheme.
                val downloadCacheDataSource = CacheDataSource(downloadCache, null) // Cache-only

                // The source for everything else (network streaming, local files).
                val defaultSource = mediaCacheDataSourceFactory.createDataSource()

                return object : DataSource by defaultSource {
                    override fun open(dataSpec: DataSpec): Long {
                        // --- FIX: Add a specific case for the 'placeholder' scheme ---
                        return when (dataSpec.uri.scheme) {
                            "cache" -> {
                                // If it's our special download scheme, use the download cache source.
                                Timber.d("DataSource: Routing 'cache://' to download cache. Key: ${dataSpec.uri.authority}")
                                val newSpec = dataSpec.buildUpon().setKey(dataSpec.uri.authority).build()
                                downloadCacheDataSource.open(newSpec)
                            }
                            "placeholder" -> {
                                // If it's a placeholder, don't open anything. Return 0 bytes.
                                // This prevents the HttpDataSource from ever seeing it.
                                Timber.d("DataSource: Intercepting 'placeholder://' URI. Returning 0.")
                                0
                            }
                            else -> {
                                // For everything else (http, https, content), use the default source.
                                Timber.d("DataSource: Routing '${dataSpec.uri.scheme}' to default/media cache source.")
                                defaultSource.open(dataSpec)
                            }
                        }
                        // --- END OF FIX ---
                    }
                }
            }
        }
        // --- END OF REPLACEMENT ---
    }


    @Provides
    @Singleton
    fun provideDefaultMediaSourceFactory(@ApplicationContext context: Context, dataSourceFactory: DataSource.Factory): DefaultMediaSourceFactory {
        return DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    }

    @Provides
    @Singleton
    fun providePreloadManager(
        @ApplicationContext context: Context,
        statusController: PreloadStatusController,
        mediaSourceFactory: DefaultMediaSourceFactory,
        loadControl: DefaultLoadControl
    ): DefaultPreloadManager {
        return DefaultPreloadManager.Builder(context, statusController)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
    }

    @Provides
    @Singleton
    fun provideMedia3PlayerController(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer,
        preloadManager: DefaultPreloadManager
    ): Media3PlayerController {
        return Media3PlayerController(exoPlayer, preloadManager)
    }

    @Provides
    @Singleton
    fun providePlaybackStateRepository(dao: PersistedPlaybackStateDao): PlaybackStateRepository {
        return RoomPlaybackStateRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideStreamResolverRepository(repo: com.example.holodex.data.repository.YouTubeStreamRepository): StreamResolverRepository {
        return HolodexStreamResolverRepositoryImpl(repo)
    }

    @Provides
    @Singleton
    fun providePlaybackRepository(
        playerController: Media3PlayerController,
        queueManager: PlaybackQueueManager,
        streamResolver: StreamResolutionCoordinator,
        progressTracker: PlaybackProgressTracker,
        persistenceManager: PlaybackStatePersistenceManager,
        continuationManager: ContinuationManager,
        mediaItemMapper: MediaItemMapper,
        downloadRepository: DownloadRepository,
        holodexRepository: HolodexRepository,
        userPreferencesRepository: UserPreferencesRepository,
        @ApplicationScope scope: CoroutineScope
    ): PlaybackRepository {
        return Media3PlaybackRepositoryImpl(
            playerController,
            queueManager,
            streamResolver,
            progressTracker,
            persistenceManager,
            continuationManager,
            mediaItemMapper,
            downloadRepository,
            holodexRepository,
            userPreferencesRepository,
            scope
        )
    }

    @Provides
    @Singleton
    fun provideMediaItemMapper(@ApplicationContext context: Context, sharedPreferences: SharedPreferences): MediaItemMapper = MediaItemMapper(context, sharedPreferences)

    @Provides
    @Singleton
    fun provideStreamResolutionCoordinator(repo: StreamResolverRepository, dao: DownloadedItemDao): StreamResolutionCoordinator = StreamResolutionCoordinator(repo, dao)

    @Provides
    @Singleton
    fun provideShuffleOrderProvider(): ShuffleOrderProvider = ShuffleOrderProvider()

    @Provides
    @Singleton
    fun providePlaybackQueueManager(provider: ShuffleOrderProvider): PlaybackQueueManager =
        PlaybackQueueManager(provider)

    @Provides
    @Singleton
    fun providePlaybackProgressTracker(
        player: Player,
        @ApplicationScope scope: CoroutineScope,
        holodexRepository: HolodexRepository,
        mediaItemMapper: MediaItemMapper
    ): PlaybackProgressTracker {
        return PlaybackProgressTracker(
            player,
            scope,
            holodexRepository,
            mediaItemMapper
        )
    }

    @Provides
    @Singleton
    fun providePlaybackStatePersistenceManager(
        repo: PlaybackStateRepository,
        @ApplicationScope scope: CoroutineScope
    ): PlaybackStatePersistenceManager = PlaybackStatePersistenceManager(repo, scope)

    @Provides
    @Singleton
    fun provideAutoplayItemProvider(holodexRepository: HolodexRepository): AutoplayItemProvider {
        return AutoplayItemProvider(holodexRepository)
    }

    @Provides
    @Singleton
    fun provideContinuationManager(
        holodexRepository: HolodexRepository,
        userPreferencesRepository: UserPreferencesRepository,
        autoplayItemProvider: AutoplayItemProvider
    ): ContinuationManager {
        return ContinuationManager(holodexRepository, userPreferencesRepository, autoplayItemProvider)
    }

    @Provides
    @Singleton
    fun providePreloadConfig(): PreloadConfiguration = PreloadConfiguration()

    @Provides
    @Singleton
    fun providePreloadStatusController(
        queueManager: PlaybackQueueManager,
        config: PreloadConfiguration
    ): PreloadStatusController {
        return PreloadStatusController(
            getCurrentIndex = { queueManager.playbackQueueFlow.value.currentIndex },
            preloadDurationMs = config.preloadDurationMs
        )
    }
}