package com.example.holodex.di

import android.app.Application
import com.example.holodex.data.db.AppDatabase
import com.example.holodex.data.db.ParentVideoMetadataDao
import com.example.holodex.data.db.PlaylistDao
import com.example.holodex.data.db.StarredPlaylistDao
import com.example.holodex.data.db.SyncMetadataDao
import com.example.holodex.data.db.UnifiedDao
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

    // *** THE FIX: Add the provider for the new DAO ***
    @Provides
    fun provideUnifiedDao(db: AppDatabase): UnifiedDao = db.unifiedDao()

    // Keep the rest of the DAOs that are still in use
    @Provides fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideParentVideoMetadataDao(db: AppDatabase): ParentVideoMetadataDao = db.parentVideoMetadataDao()
    @Provides fun provideSyncMetadataDao(db: AppDatabase): SyncMetadataDao = db.syncMetadataDao()
    @Provides fun provideStarredPlaylistDao(db: AppDatabase): StarredPlaylistDao = db.starredPlaylistDao()
}