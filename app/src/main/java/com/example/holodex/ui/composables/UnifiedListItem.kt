package com.example.holodex.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.domain.action.GlobalMediaActionHandler
import com.example.holodex.util.guessAspectRatioFromUrl
import com.example.holodex.viewmodel.UnifiedDisplayItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedListItem(
    item: UnifiedDisplayItem,
    actions: GlobalMediaActionHandler,
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    enableMarquee: Boolean = false, // <--- NEW PARAMETER
    onRemoveClicked: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    onItemClick: (() -> Unit)? = null
) {
    var currentUrlIndex by remember(item.artworkUrls) { mutableIntStateOf(0) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    val currentUrl = item.artworkUrls.getOrNull(currentUrlIndex)

    var imageAspectRatio by remember(currentUrl) {
        mutableFloatStateOf(guessAspectRatioFromUrl(currentUrl))
    }

    val context = LocalContext.current

    val menuState = remember(item) {
        ItemMenuState(
            isDownloaded = item.isDownloaded,
            downloadStatus = item.downloadStatus, // Pass status
            isSegment = item.isSegment,
            canBeDownloaded = !item.isDownloaded && item.downloadStatus != "DOWNLOADING" && item.downloadStatus != "ENQUEUED",
            shareUrl = if (item.isSegment && item.songStartSec != null) "https://music.holodex.net/watch/${item.navigationVideoId}/${item.songStartSec}" else "https://music.holodex.net/watch/${item.navigationVideoId}",
            videoId = item.navigationVideoId,
            channelId = item.channelId
        )
    }

    val menuActions = remember(item, actions) {
        ItemMenuActions(
            onAddToQueue = { actions.onQueue(item) },
            onAddToPlaylist = { actions.onAddToPlaylist(item) },
            onShare = { actions.onShare(item) },
            onDownload = { actions.onDownload(item) },
            onDelete = { actions.onDeleteDownload(item) },
            onRetryDownload = { actions.onRetryDownload(item) }, // Wire action
            onGoToVideo = { actions.onNavigateToVideo(it) },
            onGoToArtist = { actions.onNavigateToChannel(it) }
        )
    }

    Card(
        onClick = {
            if (onItemClick != null) onItemClick()
            else actions.onPlay(item)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- 1. THUMBNAIL ---
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .height(56.dp)
                    .aspectRatio(imageAspectRatio)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentUrl)
                        .crossfade(true)
                        .size(300, 300)
                        .listener(
                            onSuccess = { _, result ->
                                val w = result.drawable.intrinsicWidth.toFloat()
                                val h = result.drawable.intrinsicHeight.toFloat()
                                if (w > 0 && h > 0) {
                                    val ratio = w / h
                                    if (ratio in 0.9f..1.1f && imageAspectRatio > 1.2f) {
                                        imageAspectRatio = 1f
                                    } else if (ratio > 1.5f && imageAspectRatio < 1.2f) {
                                        imageAspectRatio = 16f / 9f
                                    }
                                }
                            },
                            onError = { _, _ ->
                                if (currentUrlIndex < item.artworkUrls.lastIndex) {
                                    currentUrlIndex++
                                }
                            }
                        )
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Duration Badge
                if (item.durationText.isNotEmpty() && imageAspectRatio > 0.8f) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.durationText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // --- 2. TEXT ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(if (enableMarquee) Modifier.basicMarquee() else Modifier)
                    )

                    if (item.downloadStatus == "COMPLETED") {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.CloudDone,
                            "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    } else if (item.downloadStatus == "FAILED" || item.downloadStatus == "EXPORT_FAILED") {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Error,
                            "Download Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = item.artistText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // FIX: Conditionally add Marquee
                    modifier = if (enableMarquee) Modifier.basicMarquee() else Modifier
                )

                if (!item.isSegment && (item.songCount ?: 0) > 0) {
                    Text(
                        stringResource(R.string.song_count_format, item.songCount ?: 0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // --- 3. BUTTONS ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (isEditing) {
                    IconButton(onClick = onRemoveClicked) {
                        Icon(Icons.Default.Delete, stringResource(R.string.action_remove_from_playlist), tint = MaterialTheme.colorScheme.error)
                    }
                    Box(modifier = dragHandleModifier) {
                        Icon(Icons.Default.DragHandle, stringResource(R.string.drag_to_reorder), tint = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    IconButton(
                        onClick = { actions.onToggleLike(item) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (item.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                stringResource(R.string.action_more_options),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
}