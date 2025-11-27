package com.example.holodex.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.data.model.ChannelSearchResult
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.viewmodel.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class AddChannelViewModel @Inject constructor(
    private val holodexRepository: HolodexRepository, // For Searching (NewPipe)
    private val unifiedRepository: UnifiedVideoRepository // For Saving (DB)
) : ViewModel() {

    // UI States
    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<UiState<List<ChannelSearchResult>>>(UiState.Success(emptyList()))
    val searchState: StateFlow<UiState<List<ChannelSearchResult>>> = _searchState.asStateFlow()

    private val _isAdding = MutableStateFlow<Set<String>>(emptySet())
    val isAdding: StateFlow<Set<String>> = _isAdding.asStateFlow()

    init {
        // Live Search Logic
        viewModelScope.launch {
            _searchQuery.debounce(500).collect { query ->
                if (query.length > 2) {
                    performChannelSearch(query)
                } else if (query.isEmpty()) {
                    _searchState.value = UiState.Success(emptyList())
                }
            }
        }
    }

    fun openDialog() { _showDialog.value = true }

    fun closeDialog() {
        _showDialog.value = false
        _searchQuery.value = ""
        _searchState.value = UiState.Success(emptyList())
    }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    private fun performChannelSearch(query: String) {
        viewModelScope.launch {
            _searchState.value = UiState.Loading
            // Search via NewPipe (External)
            holodexRepository.searchForExternalChannels(query)
                .onSuccess { results -> _searchState.value = UiState.Success(results) }
                .onFailure { error -> _searchState.value = UiState.Error(error.localizedMessage ?: "Search failed") }
        }
    }

    fun addChannel(channel: ChannelSearchResult) {
        viewModelScope.launch {
            _isAdding.value += channel.channelId

            // 1. Convert Search Result to Unified Channel Format
            val details = ChannelDetails(
                id = channel.channelId,
                name = channel.name,
                englishName = channel.name, // External channels usually don't have separate EN names
                photoUrl = channel.thumbnailUrl,
                org = "External", // <--- CRITICAL: Marks it as non-Holodex
                description = null,
                bannerUrl = null,
                suborg = null,
                twitter = null,
                group = null
            )

            // 2. Save to Unified DB
            unifiedRepository.toggleChannelLike(details)

            // 3. UI Cleanup (The Set update triggers button state, but dialog closes anyway)
            closeDialog()
            _isAdding.value -= channel.channelId
        }
    }
}