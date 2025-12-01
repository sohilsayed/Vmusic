package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.playback.domain.usecase.AddItemsToQueueUseCase
import com.example.holodex.playback.player.PlaybackController
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

data class HistoryState(
    val items: ImmutableList<UnifiedDisplayItem> = persistentListOf(),
    val isLoading: Boolean = true
)

sealed class HistorySideEffect {
    data class ShowToast(val message: String) : HistorySideEffect()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val unifiedRepository: UnifiedVideoRepository,
    private val playbackController: PlaybackController,
    private val addItemsToQueueUseCase: AddItemsToQueueUseCase
) : ContainerHost<HistoryState, HistorySideEffect>, ViewModel() {

    override val container: Container<HistoryState, HistorySideEffect> = container(HistoryState()) {
        observeHistory()
    }

    private fun observeHistory() = intent {
        // The unified repository returns items that already have liked/download status merged.
        // We just need to collect it.
        unifiedRepository.getHistory().collectLatest { historyItems ->
            reduce {
                state.copy(
                    items = historyItems.toImmutableList(),
                    isLoading = false
                )
            }
        }
    }

    fun playFromHistoryItem(tappedItem: UnifiedDisplayItem) {
        // Removed 'intent' scope for simplicity, use viewModelScope if using standard MVVM now,
        // or keep 'intent' if using Orbit.
        // Assuming Orbit 'intent':
        intent {
            val currentHistory = state.items
            val tappedIndex = currentHistory.indexOf(tappedItem)

            if (tappedIndex == -1) {
                postSideEffect(HistorySideEffect.ShowToast("Error: Could not find item to play."))
                return@intent
            }

            val playbackItems = currentHistory.map { it.toPlaybackItem() }

            // New Call:
            // Note: No need to launch a new coroutine inside intent if loadAndPlay handles scope,
            // but safe to call from here.
            playbackController.loadAndPlay(items = playbackItems, startIndex = tappedIndex)
        }
    }

    fun playAllHistory() = intent {
        val playbackItems = state.items.map { it.toPlaybackItem() }
        if (playbackItems.isNotEmpty()) {
            // New Call:
            playbackController.loadAndPlay(items = playbackItems)
        } else {
            postSideEffect(HistorySideEffect.ShowToast("History is empty."))
        }
    }

    fun addAllHistoryToQueue() = intent {
        val playbackItems = state.items.map { it.toPlaybackItem() }
        if (playbackItems.isNotEmpty()) {
            viewModelScope.launch {
                addItemsToQueueUseCase(playbackItems)
            }
            postSideEffect(HistorySideEffect.ShowToast("Added ${playbackItems.size} songs to queue."))
        } else {
            postSideEffect(HistorySideEffect.ShowToast("History is empty."))
        }
    }
}