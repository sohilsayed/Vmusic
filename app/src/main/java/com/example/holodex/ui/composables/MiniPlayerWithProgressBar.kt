// File: java/com/example/holodex/ui/composables/MiniPlayerWithProgressBar.kt

package com.example.holodex.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.generateArtworkUrlList
import com.example.holodex.viewmodel.PlaybackViewModel
import com.example.holodex.viewmodel.rememberFullPlayerLoadingState
import com.example.holodex.viewmodel.rememberIsPlayingState
import com.example.holodex.viewmodel.rememberMiniPlayerArtistState
import com.example.holodex.viewmodel.rememberMiniPlayerProgressState
import com.example.holodex.viewmodel.rememberMiniPlayerQueueStateForButton
import com.example.holodex.viewmodel.rememberMiniPlayerTitleState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayerWithProgressBar(
    playbackViewModel: PlaybackViewModel,
    modifier: Modifier = Modifier,
    onTapped: () -> Unit = {}
) {
    val uiState by playbackViewModel
        .uiState
        .collectAsStateWithLifecycle()
    val currentItem = uiState.currentItem

    val title by rememberMiniPlayerTitleState(playbackViewModel.uiState)
    val artist by rememberMiniPlayerArtistState(playbackViewModel.uiState)
    val isPlaying by rememberIsPlayingState(playbackViewModel.uiState)
    val progressFraction by rememberMiniPlayerProgressState(playbackViewModel.uiState)
    val queueStatePair by rememberMiniPlayerQueueStateForButton(playbackViewModel.uiState)
    // FIX 2: Deconstruct to ignore the unused variable
    val (_, canSkipNext) = queueStatePair
    val isLoading by rememberFullPlayerLoadingState(playbackViewModel.uiState)

    // FIX 3: Simplify this check. If title is not null, an item exists.
    val currentItemExists = title != null

    var showPlayer by remember { mutableStateOf(true) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.StartToEnd || it == SwipeToDismissBoxValue.EndToStart) {
                showPlayer = false
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(showPlayer) {
        if (!showPlayer) {
            delay(300)
            playbackViewModel.clearCurrentQueue()
        }
    }

    LaunchedEffect(currentItem?.id) {
        if (currentItem != null && !showPlayer) {
            showPlayer = true
            dismissState.reset()
        }
    }

    if (!currentItemExists && !isLoading) {
        Spacer(modifier = modifier.height(0.dp))
        return
    }

    AnimatedVisibility(
        visible = showPlayer,
        exit = shrinkVertically() + fadeOut()
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
            modifier = modifier
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clickable(onClick = onTapped, enabled = currentItemExists),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                tonalElevation = 3.dp,
                shadowElevation = 3.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 8.dp,
                                end = 4.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val miniPlayerArtworkUrls = remember(currentItem) {
                            generateArtworkUrlList(currentItem, ThumbnailQuality.HIGH)
                        }

                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(miniPlayerArtworkUrls.firstOrNull())
                                .placeholder(R.drawable.ic_default_album_art_placeholder)
                                .error(R.drawable.ic_error_image)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.content_desc_album_art),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title ?: stringResource(R.string.loading_track),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // FIX 1: Use `let` to create a local, smart-casted variable
                            artist?.let { artistText ->
                                Text(
                                    text = artistText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { playbackViewModel.togglePlayPause() },
                            enabled = currentItemExists && !isLoading
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { playbackViewModel.skipToNext() },
                            enabled = canSkipNext && !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = stringResource(R.string.action_next),
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isLoading && !currentItemExists) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                    } else if (currentItemExists) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}