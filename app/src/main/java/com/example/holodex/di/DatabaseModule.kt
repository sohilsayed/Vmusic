// File: java/com/example/holodex/di/DatabaseModule.kt
package com.example.holodex.di

import android.app.Application
import com.example.holodex.data.db.AppDatabase
import com.example.holodex.data.db.BrowsePageDao
import com.example.holodex.data.db.DiscoveryDao
import com.example.holodex.data.db.DownloadedItemDao
import com.example.holodex.data.db.FavoriteChannelDao
import com.example.holodex.data.db.HistoryDao
import com.example.holodex.data.db.LikedItemDao
import com.example.holodex.data.db.LocalDao
import com.example.holodex.data.db.ParentVideoMetadataDao
import com.example.holodex.data.db.PlaylistDao
import com.example.holodex.data.db.SearchPageDao
import com.example.holodex.data.db.StarredPlaylistDao
import com.example.holodex.data.db.SyncMetadataDao
import com.example.holodex.data.db.VideoDao
import com.example.holodex.playback.data.model.PersistedPlaybackStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase = AppDatabase.getDatabase(app)

    @Provides
    fun provideVideoDao(db: AppDatabase): VideoDao = db.videoDao()

    @Provides
    fun provideLikedItemDao(db: AppDatabase): LikedItemDao = db.likedItemDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideBrowsePageDao(db: AppDatabase): BrowsePageDao = db.browsePageDao()

    @Provides
    fun provideSearchPageDao(db: AppDatabase): SearchPageDao = db.searchPageDao()

    @Provides
    fun provideDownloadedItemDao(db: AppDatabase): DownloadedItemDao = db.downloadedItemDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideFavoriteChannelDao(db: AppDatabase): FavoriteChannelDao = db.favoriteChannelDao()

    @Provides
    fun provideDiscoveryDao(db: AppDatabase): DiscoveryDao = db.discoveryDao()

    @Provides
    fun provideLocalDao(db: AppDatabase): LocalDao = db.localDao()

    @Provides
    fun provideParentVideoMetadataDao(db: AppDatabase): ParentVideoMetadataDao =
        db.parentVideoMetadataDao()

    @Provides
    fun providePersistedPlaybackStateDao(db: AppDatabase): PersistedPlaybackStateDao =
        db.persistedPlaybackStateDao()

    @Provides
    fun provideSyncMetadataDao(db: AppDatabase): SyncMetadataDao = db.syncMetadataDao()

    @Provides
    fun provideStarredPlaylistDao(db: AppDatabase): StarredPlaylistDao = db.starredPlaylistDao()
}