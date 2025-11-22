// File: java/com/example/holodex/di/UseCaseModule.kt
package com.example.holodex.di

import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.playback.domain.repository.PlaybackRepository
import com.example.holodex.playback.domain.repository.PlaybackStateRepository
import com.example.holodex.playback.domain.repository.StreamResolverRepository
import com.example.holodex.playback.domain.usecase.AddItemToQueueUseCase
import com.example.holodex.playback.domain.usecase.AddItemsToQueueUseCase
import com.example.holodex.playback.domain.usecase.AddOrFetchAndAddUseCase
import com.example.holodex.playback.domain.usecase.ClearQueueUseCase
import com.example.holodex.playback.domain.usecase.GetPlayerSessionIdUseCase
import com.example.holodex.playback.domain.usecase.LoadPlaybackStateUseCase
import com.example.holodex.playback.domain.usecase.ObserveCurrentPlayingItemUseCase
import com.example.holodex.playback.domain.usecase.ObservePlaybackProgressUseCase
import com.example.holodex.playback.domain.usecase.ObservePlaybackQueueUseCase
import com.example.holodex.playback.domain.usecase.ObservePlaybackStateUseCase
import com.example.holodex.playback.domain.usecase.PausePlaybackUseCase
import com.example.holodex.playback.domain.usecase.PlayItemsUseCase
import com.example.holodex.playback.domain.usecase.ReleasePlaybackResourcesUseCase
import com.example.holodex.playback.domain.usecase.RemoveItemFromQueueUseCase
import com.example.holodex.playback.domain.usecase.ReorderQueueItemUseCase
import com.example.holodex.playback.domain.usecase.ResolveStreamUrlUseCase
import com.example.holodex.playback.domain.usecase.ResumePlaybackUseCase
import com.example.holodex.playback.domain.usecase.SavePlaybackStateUseCase
import com.example.holodex.playback.domain.usecase.SeekPlaybackUseCase
import com.example.holodex.playback.domain.usecase.SetRepeatModeUseCase
import com.example.holodex.playback.domain.usecase.SetScrubbingUseCase
import com.example.holodex.playback.domain.usecase.SetShuffleModeUseCase
import com.example.holodex.playback.domain.usecase.SkipToNextItemUseCase
import com.example.holodex.playback.domain.usecase.SkipToPreviousItemUseCase
import com.example.holodex.playback.domain.usecase.SkipToQueueItemUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun providePlayItemsUseCase(repo: PlaybackRepository) = PlayItemsUseCase(repo)
    @Provides
    fun providePausePlaybackUseCase(repo: PlaybackRepository) = PausePlaybackUseCase(repo)
    @Provides
    fun provideResumePlaybackUseCase(repo: PlaybackRepository) = ResumePlaybackUseCase(repo)
    @Provides
    fun provideSeekPlaybackUseCase(repo: PlaybackRepository) = SeekPlaybackUseCase(repo)
    @Provides
    fun provideSkipToNextItemUseCase(repo: PlaybackRepository) = SkipToNextItemUseCase(repo)
    @Provides
    fun provideSkipToPreviousItemUseCase(repo: PlaybackRepository) = SkipToPreviousItemUseCase(repo)
    @Provides
    fun provideSetRepeatModeUseCase(repo: PlaybackRepository) = SetRepeatModeUseCase(repo)
    @Provides
    fun provideSetShuffleModeUseCase(repo: PlaybackRepository) = SetShuffleModeUseCase(repo)
    @Provides
    fun provideSetScrubbingUseCase(repo: PlaybackRepository) = SetScrubbingUseCase(repo)
    @Provides
    fun provideReleasePlaybackResourcesUseCase(repo: PlaybackRepository) =
        ReleasePlaybackResourcesUseCase(repo)

    @Provides
    fun provideGetPlayerSessionIdUseCase(repo: PlaybackRepository) = GetPlayerSessionIdUseCase(repo)

    @Provides
    fun provideObservePlaybackStateUseCase(repo: PlaybackRepository) =
        ObservePlaybackStateUseCase(repo)

    @Provides
    fun provideObservePlaybackProgressUseCase(repo: PlaybackRepository) =
        ObservePlaybackProgressUseCase(repo)

    @Provides
    fun provideObserveCurrentPlayingItemUseCase(repo: PlaybackRepository) =
        ObserveCurrentPlayingItemUseCase(repo)

    @Provides
    fun provideObservePlaybackQueueUseCase(repo: PlaybackRepository) =
        ObservePlaybackQueueUseCase(repo)

    @Provides
    fun provideAddItemToQueueUseCase(repo: PlaybackRepository) = AddItemToQueueUseCase(repo)
    @Provides
    fun provideAddItemsToQueueUseCase(repo: PlaybackRepository) = AddItemsToQueueUseCase(repo)
    @Provides
    fun provideRemoveItemFromQueueUseCase(repo: PlaybackRepository) =
        RemoveItemFromQueueUseCase(repo)

    @Provides
    fun provideReorderQueueItemUseCase(repo: PlaybackRepository) = ReorderQueueItemUseCase(repo)
    @Provides
    fun provideClearQueueUseCase(repo: PlaybackRepository) = ClearQueueUseCase(repo)
    @Provides
    fun provideSkipToQueueItemUseCase(repo: PlaybackRepository) = SkipToQueueItemUseCase(repo)

    @Provides
    fun provideLoadPlaybackStateUseCase(repo: PlaybackStateRepository) =
        LoadPlaybackStateUseCase(repo)

    @Provides
    fun provideSavePlaybackStateUseCase(repo: PlaybackStateRepository) =
        SavePlaybackStateUseCase(repo)

    @Provides
    fun provideResolveStreamUrlUseCase(repo: StreamResolverRepository) =
        ResolveStreamUrlUseCase(repo)

    @Provides
    fun provideAddOrFetchAndAddUseCase(
        repo: HolodexRepository,
        addItemsUseCase: AddItemsToQueueUseCase
    ) = AddOrFetchAndAddUseCase(repo, addItemsUseCase)
}