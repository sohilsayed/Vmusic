// File: java/com/example/holodex/ui/composables/UnifiedListItem.kt
package com.example.holodex.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.viewmodel.DownloadsViewModel
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.VideoDetailsViewModel
import com.example.holodex.viewmodel.VideoListViewModel
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import org.orbitmvi.orbit.compose.collectAsState

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedListItem(
    item: UnifiedDisplayItem,
    onItemClicked: () -> Unit,
    navController: NavController,
    videoListViewModel: VideoListViewModel,
    favoritesViewModel: FavoritesViewModel,
    playlistManagementViewModel: PlaylistManagementViewModel,
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    onRemoveClicked: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    isExternal: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    var currentUrlIndex by remember(item.artworkUrls) { mutableIntStateOf(0) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    val favoritesState by favoritesViewModel.collectAsState()


    val isItemLiked = remember(item.playbackItemId, favoritesState.likedItemsMap) {
        favoritesState.likedItemsMap.containsKey(item.playbackItemId)
    }
    val videoDetailsViewModel: VideoDetailsViewModel = hiltViewModel()
    val downloadsViewModel: DownloadsViewModel = hiltViewModel()

    val menuState = remember(item) {
        ItemMenuState(
            isDownloaded = item.isDownloaded,
            isSegment = item.isSegment,
            canBeDownloaded = item.isSegment && !item.isDownloaded,
            shareUrl = if (item.isSegment && item.songStartSec != null) {
                "https://music.holodex.net/watch/${item.videoId}/${item.songStartSec}"
            } else {
                "https://music.holodex.net/watch/${item.videoId}"
            },
            videoId = item.videoId,
            channelId = item.channelId
        )
    }

    val menuActions = remember(item) {
        ItemMenuActions(
            onAddToQueue = { videoListViewModel.addVideoOrItsSegmentsToQueue(item.toPlaybackItem()) },
            onAddToPlaylist = {
                playlistManagementViewModel.prepareItemForPlaylistAddition(item)
            },
            onShare = { /* Handled internally by the menu now */ },
            onDownload = { videoDetailsViewModel.requestDownloadForSongFromPlaybackItem(item.toPlaybackItem()) },
            onDelete = { downloadsViewModel.deleteDownload(item.playbackItemId) },
            onGoToVideo = { videoId -> navController.navigate(AppDestinations.videoDetailRoute(videoId)) },
            onGoToArtist = { channelId -> navController.navigate("channel_details/$channelId") },

        )
    }

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onItemClicked()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.artworkUrls.getOrNull(currentUrlIndex))
                    .placeholder(R.drawable.ic_placeholder_image)
                    .error(R.drawable.ic_error_image)
                    .crossfade(true)
                    .build(),
                onError = {
                    if (currentUrlIndex < item.artworkUrls.lastIndex) {
                        currentUrlIndex++
                    }
                },
                contentDescription = stringResource(R.string.content_desc_channel_thumbnail),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isSegment && item.isDownloaded) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = item.artistText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.durationText.isNotEmpty()) {
                        Text(
                            text = item.durationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!item.isSegment) {
                        item.songCount?.let { count ->
                            if (count > 0) {
                                if (item.durationText.isNotEmpty()) {
                                    Text(
                                        " • ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    stringResource(R.string.song_count_format, count),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (isEditing) {
                IconButton(onClick = onRemoveClicked) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_remove_from_playlist),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Box(modifier = dragHandleModifier) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.drag_to_reorder)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        val playbackItem = item.toPlaybackItem().copy(isExternal = isExternal)
                        favoritesViewModel.toggleLike(playbackItem)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isItemLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isItemLiked) stringResource(R.string.action_unlike) else stringResource(R.string.action_like),
                        tint = if (isItemLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showOptionsMenu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more_options))
                    }
                    ItemOptionsMenu(
                        state = menuState,
                        actions = menuActions,
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    )
                }
            }
        }
    }
}