// File: java\com\example\holodex\di\RepositoryModule.kt

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
import com.example.holodex.data.db.FavoriteChannelDao
import com.example.holodex.data.db.HistoryDao
import com.example.holodex.data.db.LikedItemDao
import com.example.holodex.data.db.LocalDao
import com.example.holodex.data.db.PlaylistDao
import com.example.holodex.data.db.StarredPlaylistDao
import com.example.holodex.data.db.SyncMetadataDao
import com.example.holodex.data.db.VideoDao
import com.example.holodex.data.repository.DownloadRepository
import com.example.holodex.data.repository.DownloadRepositoryImpl
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.LocalRepository
import com.example.holodex.data.repository.SearchHistoryRepository
import com.example.holodex.data.repository.SharedPreferencesSearchHistoryRepository
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
            // --- The existing dependencies are correct ---
            holodexApiService: HolodexApiService,
            musicdexApiService: MusicdexApiService,
            authenticatedMusicdexApiService: AuthenticatedMusicdexApiService,
            discoveryDao: DiscoveryDao,
            browseListCache: BrowseListCache,
            searchListCache: SearchListCache,
            videoDao: VideoDao,
            likedItemDao: LikedItemDao,
            playlistDao: PlaylistDao,
            appDatabase: AppDatabase,
            historyDao: HistoryDao,
            favoriteChannelDao: FavoriteChannelDao,
            syncMetadataDao: SyncMetadataDao,
            starredPlaylistDao: StarredPlaylistDao,
            tokenManager: TokenManager,

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
                likedItemDao,
                playlistDao,
                appDatabase,
                Dispatchers.IO,
                historyDao,
                favoriteChannelDao,
                syncMetadataDao,
                starredPlaylistDao,
                tokenManager,
                applicationScope
            )
        }

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
        fun provideLocalRepository(localDao: com.example.holodex.data.db.LocalDao): LocalRepository {
            return LocalRepository(localDao as LocalDao)
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