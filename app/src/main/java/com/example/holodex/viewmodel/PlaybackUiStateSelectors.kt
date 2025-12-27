package com.example.holodex.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.holodex.playback.domain.model.DomainPlaybackProgress
import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.generateArtworkUrlList
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.getValue


@Composable
fun rememberMiniPlayerTitleState(uiStateFlow: StateFlow<PlaybackUiState>): State<String?> {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()
    return remember(uiState.currentItem?.id) { mutableStateOf(uiState.currentItem?.title) }
}

@Composable
fun rememberMiniPlayerArtistState(uiStateFlow: StateFlow<PlaybackUiState>): State<String?> {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()
    return remember(uiState.currentItem?.id) { mutableStateOf(uiState.currentItem?.artistText) }
}

@Composable
fun rememberIsPlayingState(uiStateFlow: StateFlow<PlaybackUiState>): State<Boolean> {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()
    return remember(uiState.isPlaying) { mutableStateOf(uiState.isPlaying) }
}

@Composable
fun rememberMiniPlayerProgressState(uiStateFlow: StateFlow<PlaybackUiState>): State<Float> {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()
    return remember(uiState.progress, uiState.currentItem?.id) {
        val progressFraction = if (uiState.currentItem != null && uiState.progress.durationSec > 0) {
            (uiState.progress.positionSec.toFloat() / uiState.progress.durationSec.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        // FIX: Use mutableFloatStateOf for performance primitive avoidance
        mutableFloatStateOf(progressFraction)
    }
}

@Composable
fun rememberMiniPlayerQueueStateForButton(uiStateFlow: StateFlow<PlaybackUiState>): State<Pair<Boolean, Boolean>> {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()
    return remember(uiState.queue, uiState.currentItem, uiState.currentIndexInQueue, uiState.repeatMode) {
        val hasItemAndQueue = uiState.queue.isNotEmpty() && uiState.currentItem != null
        val canSkipNext = hasItemAndQueue &&
                (uiState.currentIndexInQueue < uiState.queue.size - 1 || uiState.queue.size == 1 && uiState.repeatMode != DomainRepeatMode.ONE)
        mutableStateOf(hasItemAndQueue to canSkipNext)
    }
}

// --- Selectors for FullPlayerScreen ---

@Composable
fun rememberFullPlayerArtworkState(
    uiStateFlow: StateFlow<PlaybackUiState>
): State<String?> {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()

    return remember(uiState.currentItem?.id, uiState.currentItem?.artworkUri) {
        // FullPlayer: Max Quality
        val urls = generateArtworkUrlList(uiState.currentItem, ThumbnailQuality.MAX)
        mutableStateOf(urls.firstOrNull())
    }
}

@Composable
fun rememberFullPlayerCurrentItemState(uiStateFlow: StateFlow<PlaybackUiState>): State<PlaybackItem?> {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()
    return remember(uiState.currentItem) { mutableStateOf(uiState.currentItem) }
}

@Composable
fun rememberFullPlayerProgressState(uiStateFlow: StateFlow<PlaybackUiState>): State<DomainPlaybackProgress> {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()
    return remember(uiState.progress) { mutableStateOf(uiState.progress) }
}

@Composable
fun rememberFullPlayerQueueInfoState(uiStateFlow: StateFlow<PlaybackUiState>): State<Triple<List<PlaybackItem>, Int, Boolean>> {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()
    return remember(uiState.queue, uiState.currentIndexInQueue) {
        mutableStateOf(Triple(uiState.queue, uiState.currentIndexInQueue, uiState.queue.isNotEmpty()))
    }
}

@Composable
fun rememberFullPlayerLoadingState(uiStateFlow: StateFlow<PlaybackUiState>): State<Boolean> {
    val uiState by uiStateFlow.collectAsStateWithLifecycle()
    return remember(uiState.isLoading) { mutableStateOf(uiState.isLoading) }
}