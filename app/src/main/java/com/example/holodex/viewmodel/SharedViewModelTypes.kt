package com.example.holodex.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job

data class SubOrgHeader(val name: String)

enum class MusicCategoryType {
    LATEST,
    UPCOMING_MUSIC,
    SEARCH,
    FAVORITES,
    LIKED_SEGMENTS,
    TRENDING,
    RECENT_STREAMS,
    COMMUNITY_PLAYLISTS,
    ARTIST_RADIOS,
    SYSTEM_PLAYLISTS,
    DISCOVER_CHANNELS
}

// Helper for manual pagination (used by FullListViewModel)
class ListStateHolder<T> {
    val items: MutableState<List<T>> = mutableStateOf(emptyList())
    val isLoadingInitial: MutableState<Boolean> = mutableStateOf(false)
    val isLoadingMore: MutableState<Boolean> = mutableStateOf(false)
    val endOfList: MutableState<Boolean> = mutableStateOf(false)
    var currentOffset: Int = 0
    var job: Job? = null
}