package com.example.holodex.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.holodex.data.cache.BrowseListCache
import com.example.holodex.data.cache.SearchListCache
import com.example.holodex.data.db.BrowsePageDao
import com.example.holodex.data.db.SearchPageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideBrowseListCache(browsePageDao: BrowsePageDao): BrowseListCache {
        return BrowseListCache(browsePageDao)
    }

    @Provides
    @Singleton
    fun provideSearchListCache(searchPageDao: SearchPageDao): SearchListCache {
        return SearchListCache(searchPageDao)
    }

    @Provides
    @Singleton
    @DownloadCache
    @UnstableApi
    fun provideDownloadCache(@ApplicationContext context: Context): SimpleCache {
        val downloadDirectory = File(context.getExternalFilesDir(null), "downloads")
        val databaseProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)
    }

    @Provides
    @Singleton
    @MediaCache
    @UnstableApi
    fun provideMediaCache(@ApplicationContext context: Context): SimpleCache {
        val mediaCacheDirectory = File(context.cacheDir, "exoplayer_media_cache")
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(150L * 1024L * 1024L) // 150MB
        val databaseProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(mediaCacheDirectory, cacheEvictor, databaseProvider)
    }
}