package com.example.holodex.ui.navigation

import com.example.holodex.viewmodel.UnifiedDisplayItem

sealed class GlobalUiEvent {
    data class NavigateToVideo(val videoId: String) : GlobalUiEvent()
    data class NavigateToChannel(val channelId: String) : GlobalUiEvent()
    data class ShowPlaylistDialog(val item: UnifiedDisplayItem) : GlobalUiEvent()
    data class ShowToast(val message: String) : GlobalUiEvent()
}