// File: java/com/example/holodex/playback/data/queue/PlaybackQueueManager.kt
package com.example.holodex.playback.data.queue

import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.domain.model.PlaybackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class PlaybackQueueManager(
    private val shuffleOrderProvider: ShuffleOrderProvider
) {
    private val _playbackQueueFlow = MutableStateFlow(PlaybackQueueState())
    val playbackQueueFlow: StateFlow<PlaybackQueueState> = _playbackQueueFlow.asStateFlow()

    companion object {
        private const val TAG = "QUEUE_SHUFFLE_DEBUG"
    }

    fun dispatch(action: QueueAction) {
        val currentState = _playbackQueueFlow.value
        Timber.tag(TAG).i("[QueueManager] dispatch() received Action: ${action::class.simpleName}")
        val newState = reduce(currentState, action)
        _playbackQueueFlow.value = newState
    }

    private fun calculateNextIndex(currentState: PlaybackQueueState): Int {
        if (currentState.activeList.isEmpty()) return -1
        return when (currentState.repeatMode) {
            DomainRepeatMode.ONE -> currentState.currentIndex // Repeat one just stays on the same index
            DomainRepeatMode.ALL -> if (currentState.currentIndex >= currentState.activeList.size - 1) 0 else currentState.currentIndex + 1
            DomainRepeatMode.NONE -> currentState.currentIndex + 1 // This will go out of bounds, which is intentional to signal the end
        }
    }

    private fun calculatePreviousIndex(currentState: PlaybackQueueState): Int {
        if (currentState.activeList.isEmpty()) return -1
        // In all modes, going previous from the start either goes to the end (if repeating) or just stays at the start.
        return if (currentState.currentIndex <= 0) {
            if (currentState.repeatMode == DomainRepeatMode.ALL) currentState.activeList.size - 1 else 0
        } else {
            currentState.currentIndex - 1
        }
    }
    fun calculateInsertionIndex(item: PlaybackItem, requestedIndex: Int?): Int {
        val currentState = _playbackQueueFlow.value
        val insertAt = requestedIndex ?: currentState.originalList.size

        return if (currentState.shuffleMode == DomainShuffleMode.ON) {
            // In shuffle mode, new items are always added to the end of the active (shuffled) list.
            currentState.activeList.size
        } else {
            // In normal mode, add it where requested in the active (original) list.
            insertAt
        }
    }
    private fun reduce(currentState: PlaybackQueueState, action: QueueAction): PlaybackQueueState {
        return when (action) {
            is QueueAction.SetQueue -> {
                val originalList = action.items
                var activeList = originalList
                var currentIndex = action.startIndex
                var shuffleMode = action.restoredShuffleMode ?: DomainShuffleMode.OFF
                val repeatMode = action.restoredRepeatMode ?: DomainRepeatMode.NONE

                if (action.shouldShuffle) {
                    val itemToStartWith = originalList.getOrNull(action.startIndex)
                    if (itemToStartWith != null) {
                        activeList =
                            shuffleOrderProvider.createShuffledList(originalList, itemToStartWith)
                        currentIndex = 0
                        shuffleMode = DomainShuffleMode.ON
                    }
                } else if (shuffleMode == DomainShuffleMode.ON && !action.restoredShuffledList.isNullOrEmpty()) {
                    activeList = action.restoredShuffledList
                }

                return currentState.copy(
                    originalList = originalList,
                    activeList = activeList,
                    currentIndex = currentIndex,
                    shuffleMode = shuffleMode,
                    repeatMode = repeatMode,
                    transientStartPositionMs = action.startPositionMs
                )
            }

            is QueueAction.ToggleShuffle -> {
                val newShuffleMode = if (currentState.shuffleMode == DomainShuffleMode.ON) DomainShuffleMode.OFF else DomainShuffleMode.ON
                val currentItem = currentState.currentItem ?: return currentState.copy(shuffleMode = newShuffleMode)

                if (newShuffleMode == DomainShuffleMode.ON) {
                    val newActiveList = shuffleOrderProvider.createShuffledList(
                        currentState.originalList,
                        currentItem
                    )
                    return currentState.copy(
                        activeList = newActiveList,
                        currentIndex = 0,
                        shuffleMode = newShuffleMode
                    )
                } else {
                    val newActiveList = currentState.originalList
                    val newIndex = newActiveList.indexOf(currentItem).coerceAtLeast(0)
                    return currentState.copy(
                        activeList = newActiveList,
                        currentIndex = newIndex,
                        shuffleMode = newShuffleMode
                    )
                }
            }

            is QueueAction.SetRepeatMode -> currentState.copy(repeatMode = action.repeatMode)
            is QueueAction.AddItem -> {
                val insertAt = action.index ?: currentState.originalList.size
                val newOriginal = currentState.originalList.toMutableList().apply { add(insertAt, action.item) }
                // If shuffling, add to a random position in the active list (but not at the current spot)
                val newActive = if (currentState.shuffleMode == DomainShuffleMode.ON) {
                    currentState.activeList.toMutableList().apply {
                        // Add somewhere after the current item
                        val randomPosition = if (size > currentState.currentIndex + 1) {
                            (currentState.currentIndex + 1 until size).random()
                        } else {
                            size
                        }
                        add(randomPosition, action.item)
                    }
                } else {
                    currentState.activeList.toMutableList().apply { add(insertAt, action.item) }
                }
                currentState.copy(originalList = newOriginal, activeList = newActive)
            }

            // --- START OF FIX: Add the case for AddItems ---
            is QueueAction.AddItems -> {
                if (action.items.isEmpty()) return currentState

                val insertAt = action.index ?: currentState.originalList.size
                val newOriginal = currentState.originalList.toMutableList().apply { addAll(insertAt, action.items) }

                val newActive = if (currentState.shuffleMode == DomainShuffleMode.ON) {
                    // When adding multiple items to a shuffled queue, just append the new items
                    // (shuffled among themselves) to the end of the active queue.
                    currentState.activeList + action.items.shuffled()
                } else {
                    currentState.activeList.toMutableList().apply { addAll(insertAt, action.items) }
                }
                currentState.copy(originalList = newOriginal, activeList = newActive)
            }

            is QueueAction.RemoveItem -> {
                if (action.index !in currentState.activeList.indices) return currentState
                val itemToRemove = currentState.activeList[action.index]
                val newActive = currentState.activeList.toMutableList().apply { removeAt(action.index) }
                val newOriginal = currentState.originalList.toMutableList().apply { remove(itemToRemove) }
                val newCurrentIndex = when {
                    action.index < currentState.currentIndex -> currentState.currentIndex - 1
                    action.index == currentState.currentIndex -> if (currentState.currentIndex >= newActive.size) newActive.size - 1 else currentState.currentIndex
                    else -> currentState.currentIndex
                }
                currentState.copy(
                    originalList = newOriginal,
                    activeList = newActive,
                    currentIndex = newCurrentIndex
                )
            }

            is QueueAction.SetCurrentIndex -> {
                if (action.newIndex != currentState.currentIndex) {
                    currentState.copy(currentIndex = action.newIndex)
                } else {
                    currentState
                }
            }

            is QueueAction.ClearQueue -> PlaybackQueueState()

            is QueueAction.UpdateItemInQueue -> {
                return currentState.copy(
                    originalList = currentState.originalList.map { if (it.id == action.updatedItem.id) action.updatedItem else it },
                    activeList = currentState.activeList.map { if (it.id == action.updatedItem.id) action.updatedItem else it }
                )
            }
            is QueueAction.ReorderItem -> {
                if (action.fromIndex !in currentState.activeList.indices || action.toIndex !in currentState.activeList.indices) {
                    return currentState // Invalid indices, do nothing
                }
                val reorderedList = currentState.activeList.toMutableList().apply {
                    add(action.toIndex, removeAt(action.fromIndex))
                }
                // Adjust the current playing index if it was affected by the move
                val newCurrentIndex = when {
                    currentState.currentIndex == action.fromIndex -> action.toIndex
                    action.fromIndex < currentState.currentIndex && action.toIndex >= currentState.currentIndex -> currentState.currentIndex - 1
                    action.fromIndex > currentState.currentIndex && action.toIndex <= currentState.currentIndex -> currentState.currentIndex + 1
                    else -> currentState.currentIndex
                }
                return currentState.copy(activeList = reorderedList, currentIndex = newCurrentIndex)
            }
            is QueueAction.SkipToNext -> {
                val nextIndex = calculateNextIndex(currentState)
                // We only update the index if it's within the bounds of the list.
                // If it goes out of bounds, the repository will see this and know playback has ended.
                if (nextIndex in currentState.activeList.indices) {
                    currentState.copy(currentIndex = nextIndex)
                } else {
                    // Let the state reflect that we've gone past the end
                    currentState.copy(currentIndex = nextIndex)
                }
            }
            is QueueAction.SkipToPrevious -> {
                val previousIndex = calculatePreviousIndex(currentState)
                currentState.copy(currentIndex = previousIndex)
            }
            else -> currentState // Placeholder for other actions
        }
    }
}