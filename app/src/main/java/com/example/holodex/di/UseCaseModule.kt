package com.example.holodex.di

import com.example.holodex.data.repository.PlaylistRepository
import com.example.holodex.data.repository.VideoRepository
import com.example.holodex.playback.domain.usecase.AddItemsToQueueUseCase
import com.example.holodex.playback.domain.usecase.AddOrFetchAndAddUseCase
import com.example.holodex.playback.player.PlaybackController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    // We only keep complex UseCases.
    // Simple ones (Play, Pause) are handled directly by Controller in ViewModels now.

    @Provides
    fun provideAddItemsToQueueUseCase(controller: PlaybackController): AddItemsToQueueUseCase {
        return AddItemsToQueueUseCase(controller)
    }

    @Provides
    fun provideAddOrFetchAndAddUseCase(
        repo: VideoRepository,
        addItemsUseCase: AddItemsToQueueUseCase
    ) = AddOrFetchAndAddUseCase(repo, addItemsUseCase)
}