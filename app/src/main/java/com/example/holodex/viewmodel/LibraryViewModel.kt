package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedItemWithStatus // <--- USE THIS OPTIMIZED TYPE
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryType {
    HISTORY,
    DOWNLOADS,
    FAVORITES
}

data class LibraryState(
    val items: ImmutableList<UnifiedDisplayItem> = persistentListOf(),
    val searchQuery: String = "",
    val isEmpty: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val unifiedDao: UnifiedDao
) : ViewModel() {

    private val _activeType = MutableStateFlow(LibraryType.HISTORY)
    private val _searchQuery = MutableStateFlow("")

    val state: StateFlow<LibraryState> = combine(
        _activeType,
        _searchQuery
    ) { type, query ->
        Pair(type, query)
    }.flatMapLatest { (type, query) ->
        // 1. Select the Data Source based on the Tab
        // CRITICAL FIX: Use the 'Optimized' feeds that return UnifiedItemWithStatus
        // This avoids the O(N) mapping loops and makes the list scroll smoothly.
        val sourceFlow: Flow<List<UnifiedItemWithStatus>> = when (type) {
            LibraryType.HISTORY -> unifiedDao.getOptimizedHistoryFeed()
            LibraryType.DOWNLOADS -> unifiedDao.getOptimizedDownloadsFeed()
            LibraryType.FAVORITES -> unifiedDao.getOptimizedFavoritesFeed()
        }

        // 2. Transform (Map to UI Model) & Filter (Search)
        sourceFlow.combine(_searchQuery) { rawList, currentQuery ->
            val filtered = if (currentQuery.isBlank()) {
                rawList
            } else {
                rawList.filter { item ->
                    item.metadata.title.contains(currentQuery, ignoreCase = true) ||
                            item.metadata.artistName.contains(currentQuery, ignoreCase = true)
                }
            }

            LibraryState(
                // Use the optimized extension function
                items = filtered.map { it.toUnifiedDisplayItem() }.toImmutableList(),
                searchQuery = currentQuery,
                isEmpty = filtered.isEmpty(),
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryState(isLoading = true)
    )

    fun setLibraryType(type: LibraryType) {
        _activeType.value = type
        _searchQuery.value = ""
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearHistory() {
        if (_activeType.value == LibraryType.HISTORY) {
            viewModelScope.launch {
                unifiedDao.clearHistory()
            }
        }
    }
}