package com.example.holodex.di

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.example.holodex.auth.AuthRepository
import com.example.holodex.auth.TokenManager
import com.example.holodex.data.api.AuthenticatedMusicdexApiService
import com.example.holodex.data.api.HolodexApiService
import com.example.holodex.data.api.MusicdexApiService
import com.example.holodex.data.cache.BrowseListCache
import com.example.holodex.data.cache.SearchListCache
import com.example.holodex.data.db.AppDatabase
import com.example.holodex.data.db.DiscoveryDao
import com.example.holodex.data.db.HistoryDao
import com.example.holodex.data.db.PlaylistDao
import com.example.holodex.data.db.StarredPlaylistDao
import com.example.holodex.data.db.SyncMetadataDao
import com.example.holodex.data.db.UnifiedDao // Added
import com.example.holodex.data.db.VideoDao
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.DownloadRepositoryImpl
import com.example.holodex.data.repository.HolodexRepository
// LocalRepository import removed
import com.example.holodex.data.repository.SearchHistoryRepository
import com.example.holodex.data.repository.SharedPreferencesSearchHistoryRepository
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.data.repository.UserPreferencesRepository
import com.example.holodex.data.repository.YouTubeStreamRepository
import com.example.holodex.data.repository.userPreferencesDataStore
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.openid.appauth.AuthorizationService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(
        impl: SharedPreferencesSearchHistoryRepository
    ): SearchHistoryRepository

    @Binds
    @Singleton
    @UnstableApi
    abstract fun bindDownloadRepository(
        impl: DownloadRepositoryImpl
    ): DownloadRepository

    companion object {

        @Provides
        @Singleton
        fun provideHolodexRepository(
            holodexApiService: HolodexApiService,
            musicdexApiService: MusicdexApiService,
            authenticatedMusicdexApiService: AuthenticatedMusicdexApiService,
            discoveryDao: DiscoveryDao,
            browseListCache: BrowseListCache,
            searchListCache: SearchListCache,
            videoDao: VideoDao,
            playlistDao: PlaylistDao,
            appDatabase: AppDatabase,
            // *** THE FIX: Reorder arguments to match the constructor ***
            @DefaultDispatcher defaultDispatcher: CoroutineDispatcher, // Use Qualifier
            historyDao: HistoryDao,
            syncMetadataDao: SyncMetadataDao,
            starredPlaylistDao: StarredPlaylistDao,
            tokenManager: TokenManager,
            unifiedDao: UnifiedDao,
            unifiedRepository: UnifiedVideoRepository, // Added
            @ApplicationScope applicationScope: CoroutineScope
        ): HolodexRepository {
            return HolodexRepository(
                holodexApiService,
                musicdexApiService,
                authenticatedMusicdexApiService,
                discoveryDao,
                browseListCache,
                searchListCache,
                videoDao,
                playlistDao,
                appDatabase,
                defaultDispatcher, // Correct position
                historyDao,
                syncMetadataDao,
                starredPlaylistDao,
                tokenManager,
                unifiedDao,
                unifiedRepository, // Pass it here
                applicationScope
            )
        }

        // provideLocalRepository REMOVED

        @Provides
        @Singleton
        fun provideYouTubeStreamRepository(
            sharedPreferences: SharedPreferences,
        ): YouTubeStreamRepository {
            return YouTubeStreamRepository(sharedPreferences)
        }

        @Provides
        @Singleton
        fun provideAuthRepository(
            holodexApiService: HolodexApiService,
            authService: AuthorizationService
        ): AuthRepository {
            return AuthRepository(holodexApiService, authService)
        }

        @Provides
        @Singleton
        fun provideUserPreferencesRepository(@ApplicationContext context: Context): UserPreferencesRepository {
            return UserPreferencesRepository(context.userPreferencesDataStore)
        }

        @Provides
        @Singleton
        fun provideSharedPreferencesSearchHistoryRepository(
            sharedPreferences: SharedPreferences,
            gson: Gson
        ): SharedPreferencesSearchHistoryRepository {
            return SharedPreferencesSearchHistoryRepository(sharedPreferences, gson, Dispatchers.IO)
        }

        @OptIn(UnstableApi::class)
        @Provides
        @Singleton
        @UpstreamDataSource
        fun provideUpstreamDataSourceFactory(): DataSource.Factory {
            return DefaultHttpDataSource.Factory()
                .setUserAgent("HolodexAppDownloader/1.0")
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
        }
    }
}