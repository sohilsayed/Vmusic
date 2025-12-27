// File: java/com/example/holodex/di/SyncModule.kt (MODIFIED)
package com.example.holodex.di

import com.example.holodex.background.FavoriteChannelSynchronizer
import com.example.holodex.background.ISynchronizer
import com.example.holodex.background.LikesSynchronizer
import com.example.holodex.background.PlaylistSynchronizer
import com.example.holodex.background.StarredPlaylistSynchronizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @IntoSet
    abstract fun bindLikesSynchronizer(impl: LikesSynchronizer): ISynchronizer

    @Binds
    @IntoSet
    abstract fun bindPlaylistSynchronizer(impl: PlaylistSynchronizer): ISynchronizer

    @Binds
    @IntoSet
    abstract fun bindFavoriteChannelSynchronizer(impl: FavoriteChannelSynchronizer): ISynchronizer

    @Binds
    @IntoSet
    abstract fun bindStarredPlaylistSynchronizer(impl: StarredPlaylistSynchronizer): ISynchronizer


}