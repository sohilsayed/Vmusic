// File: java/com/example/holodex/ui/screens/DownloadsScreen.kt
package com.example.holodex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.DownloadedItemEntity
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.util.formatDurationSecondsToString
import com.example.holodex.ui.AppDestinations
import com.example.holodex.ui.composables.ItemMenuActions
import com.example.holodex.ui.composables.ItemMenuState
import com.example.holodex.ui.composables.ItemOptionsMenu
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.getYouTubeThumbnailUrl
import com.example.holodex.viewmodel.DownloadsViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.VideoListViewModel
import com.example.holodex.viewmodel.toUnifiedDisplayItem
import kotlinx.coroutines.launch

// Data class for clean action handling
data class DownloadItemActions(
    val onPlay: () -> Unit,
    val onDelete: () -> Unit,
    val onRetryDownload: () -> Unit, // For network failures
    val onRetryExport: () -> Unit,   // NEW: For post-processing failures
    val onCancel: () -> Unit,
    val onResume: () -> Unit,
    val playbackItem: PlaybackItem
)

// Helper function to create actions for each download item
@UnstableApi
private fun createDownloadItemActions(
    item: DownloadedItemEntity,
    downloadsViewModel: DownloadsViewModel
): DownloadItemActions {
    return DownloadItemActions(
        onPlay = { downloadsViewModel.playDownloads(item) },
        onDelete = { downloadsViewModel.deleteDownload(item.videoId) },
        onRetryDownload = { downloadsViewModel.retryDownload(item) },
        onRetryExport = { downloadsViewModel.retryExport(item) },
        onCancel = { downloadsViewModel.cancelDownload(item.videoId) },
        onResume = { downloadsViewModel.resumeDownload(item.videoId) },
        playbackItem = downloadsViewModel.mapDownloadToPlaybackItem(item)
    )
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navController: NavController,
    downloadsViewModel: DownloadsViewModel = hiltViewModel(),
    playlistManagementViewModel: PlaylistManagementViewModel,
) {
    val filteredDownloads by downloadsViewModel.filteredDownloads.collectAsStateWithLifecycle()
    val searchQuery by downloadsViewModel.searchQuery.collectAsStateWithLifecycle()
    val hasCompletedDownloads =
        filteredDownloads.any { it.downloadStatus == DownloadStatus.COMPLETED }

    var isGridView by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bottom_nav_downloads)) },
                actions = {
                    if (hasCompletedDownloads) {
                        TextButton(onClick = { downloadsViewModel.playAllDownloadsShuffled() }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = stringResource(R.string.action_play_all))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.action_play_all))
                        }
                    }
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = stringResource(if (isGridView) R.string.action_view_as_list else R.string.action_view_as_grid)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    try {
                        downloadsViewModel.purgeStaleDownloads()
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { downloadsViewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_your_downloads_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { downloadsViewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Filled.Clear, stringResource(R.string.action_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                )

                if (filteredDownloads.isEmpty()) {
                    EmptyDownloadsState(isSearching = searchQuery.isNotEmpty())
                } else {
                    if (isGridView) {
                        DownloadsGrid(
                            downloads = filteredDownloads,
                            navController = navController,
                            playlistManagementViewModel = playlistManagementViewModel,
                            downloadsViewModel = downloadsViewModel,
                            createActions = { item ->
                                createDownloadItemActions(item, downloadsViewModel)
                            }
                        )
                    } else {
                        DownloadsList(
                            downloads = filteredDownloads,
                            navController = navController,
                            playlistManagementViewModel = playlistManagementViewModel,
                            downloadsViewModel = downloadsViewModel,
                            createActions = { item ->
                                createDownloadItemActions(item, downloadsViewModel)
                            }
                        )
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun DownloadsList(
    downloads: List<DownloadedItemEntity>,
    navController: NavController,
    playlistManagementViewModel: PlaylistManagementViewModel,
    downloadsViewModel: DownloadsViewModel,
    createActions: (DownloadedItemEntity) -> DownloadItemActions
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        items(downloads, key = { it.videoId }) { item ->
            DownloadItemRow(
                item = item,
                actions = createActions(item),
                navController = navController,
                playlistManagementViewModel = playlistManagementViewModel,
                downloadsViewModel = downloadsViewModel
            )
        }
    }
}


@UnstableApi
@Composable
private fun DownloadsGrid(
    downloads: List<DownloadedItemEntity>,
    navController: NavController,
    playlistManagementViewModel: PlaylistManagementViewModel,
    downloadsViewModel: DownloadsViewModel,
    createActions: (DownloadedItemEntity) -> DownloadItemActions
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(downloads, key = { "grid_${it.videoId}" }) { item ->
            DownloadGridItem(
                item = item,
                actions = createActions(item),
                navController = navController,
                playlistManagementViewModel = playlistManagementViewModel,
                downloadsViewModel = downloadsViewModel
            )
        }
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadItemRow(
    item: DownloadedItemEntity,
    navController: NavController,
    actions: DownloadItemActions,
    playlistManagementViewModel: PlaylistManagementViewModel,
    downloadsViewModel: DownloadsViewModel
) {
    var showMenu by remember { mutableStateOf(false) }

    val videoListViewModel: VideoListViewModel = hiltViewModel()

    val videoId = remember(item.videoId) { item.videoId.split('_').first() }
    val thumbnailUrls = remember(videoId, item.artworkUrl) {
        listOfNotNull(item.artworkUrl) + getYouTubeThumbnailUrl(videoId, ThumbnailQuality.MEDIUM)
    }
    var currentUrlIndex by remember(thumbnailUrls) { mutableIntStateOf(0) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = { if (item.downloadStatus == DownloadStatus.COMPLETED) actions.onPlay() },
        enabled = item.downloadStatus == DownloadStatus.COMPLETED
    ) {
        Column {
            if (item.downloadStatus == DownloadStatus.DOWNLOADING && item.progress > 0) {
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrls.getOrNull(currentUrlIndex))
                        .placeholder(R.drawable.ic_placeholder_image)
                        .error(R.drawable.ic_error_image)
                        .crossfade(true).build(),
                    onError = {
                        if (currentUrlIndex < thumbnailUrls.lastIndex) currentUrlIndex++
                    },
                    contentDescription = "Artwork for ${item.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = item.artistText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDurationSecondsToString(item.durationSec),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.downloadStatus == DownloadStatus.DOWNLOADING && item.progress > 0) {
                            Text(
                                text = "${item.progress}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                DownloadStatusIndicator(
                    status = item.downloadStatus,
                    progress = item.progress,
                    onCancel = actions.onCancel,
                    onResume = actions.onResume,
                    onRetryDownload = actions.onRetryDownload,
                    onRetryExport = actions.onRetryExport,
                    onDelete = actions.onDelete,
                    onShowMenu = { showMenu = true }
                )

                Box {
                    val menuState = remember(item) {
                        ItemMenuState(
                            isDownloaded = true, isSegment = true, canBeDownloaded = false,
                            shareUrl = "https://music.holodex.net/watch/${item.videoId.substringBeforeLast('_')}/${item.videoId.substringAfterLast('_', "0")}",
                            videoId = item.videoId.substringBeforeLast('_'), channelId = item.channelId
                        )
                    }

                    val menuActions = remember(item) {
                        ItemMenuActions(
                            onAddToQueue = { videoListViewModel.addVideoOrItsSegmentsToQueue(actions.playbackItem) },
                            onAddToPlaylist = {
                                val unifiedItem = downloadsViewModel.mapDownloadToPlaybackItem(item).toUnifiedDisplayItem()
                                playlistManagementViewModel.prepareItemForPlaylistAddition(unifiedItem)
                            },
                            onShare = { /* Handled internally by the menu */ },
                            onDownload = { /* No-op, already downloaded */ },
                            onDelete = actions.onDelete,
                            onGoToVideo = { videoId -> navController.navigate(AppDestinations.videoDetailRoute(videoId)) },
                            onGoToArtist = { channelId -> navController.navigate("channel_details/$channelId") }
                        )
                    }

                    if (item.downloadStatus == DownloadStatus.COMPLETED) {
                        ItemOptionsMenu(
                            state = menuState,
                            actions = menuActions,
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        )
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun DownloadGridItem(
    item: DownloadedItemEntity,
    navController: NavController,
    actions: DownloadItemActions,
    playlistManagementViewModel: PlaylistManagementViewModel,
    downloadsViewModel: DownloadsViewModel
) {
    var showMenu by remember { mutableStateOf(false) }
    val videoListViewModel: VideoListViewModel = hiltViewModel()

    val videoId = remember(item.videoId) { item.videoId.split('_').first() }
    val thumbnailUrls = remember(videoId, item.artworkUrl) {
        listOfNotNull(item.artworkUrl) + getYouTubeThumbnailUrl(videoId, ThumbnailQuality.HIGH)
    }
    var currentUrlIndex by remember(thumbnailUrls) { mutableIntStateOf(0) }

    Card(
        onClick = { if (item.downloadStatus == DownloadStatus.COMPLETED) actions.onPlay() },
        enabled = item.downloadStatus == DownloadStatus.COMPLETED
    ) {
        Column {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrls.getOrNull(currentUrlIndex))
                        .placeholder(R.drawable.ic_placeholder_image)
                        .error(R.drawable.ic_error_image)
                        .crossfade(true).build(),
                    onError = { if (currentUrlIndex < thumbnailUrls.lastIndex) currentUrlIndex++ },
                    contentDescription = "Artwork for ${item.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )

                if (item.downloadStatus != DownloadStatus.COMPLETED) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (item.downloadStatus) {
                            DownloadStatus.DOWNLOADING -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                                    if (item.progress > 0) Text(text = "${item.progress}%", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                    IconButton(onClick = actions.onCancel, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.action_cancel_download), tint = Color.White, modifier = Modifier.size(20.dp)) }
                                }
                            }
                            DownloadStatus.ENQUEUED -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Schedule, contentDescription = "Queued", tint = Color.White, modifier = Modifier.size(32.dp))
                                    Text(text = stringResource(R.string.status_queued), style = MaterialTheme.typography.bodySmall, color = Color.White)
                                    IconButton(onClick = actions.onCancel, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.action_cancel_download), tint = Color.White, modifier = Modifier.size(20.dp)) }
                                }
                            }
                            DownloadStatus.PAUSED -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Pause, contentDescription = "Paused", tint = Color.White, modifier = Modifier.size(32.dp))
                                    Text(text = stringResource(R.string.status_paused), style = MaterialTheme.typography.bodySmall, color = Color.White)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(onClick = actions.onResume, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_resume_download), tint = Color.White, modifier = Modifier.size(20.dp)) }
                                        IconButton(onClick = actions.onCancel, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.action_cancel_download), tint = Color.White, modifier = Modifier.size(20.dp)) }
                                    }
                                }
                            }
                            DownloadStatus.FAILED, DownloadStatus.EXPORT_FAILED -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Error, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                                    Text(text = stringResource(R.string.status_failed), style = MaterialTheme.typography.bodySmall, color = Color.White)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(onClick = if (item.downloadStatus == DownloadStatus.FAILED) actions.onRetryDownload else actions.onRetryExport, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_retry_download), tint = Color.White, modifier = Modifier.size(20.dp)) }
                                        IconButton(onClick = actions.onDelete, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_delete), tint = Color.White, modifier = Modifier.size(20.dp)) }
                                    }
                                }
                            }
                            else -> Icon(Icons.Filled.Download, contentDescription = "Download Status", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                if (item.downloadStatus == DownloadStatus.COMPLETED) {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.action_more_options),
                                tint = Color.White,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artistText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box {
            val menuState = remember(item) {
                ItemMenuState(
                    isDownloaded = true, isSegment = true, canBeDownloaded = false,
                    shareUrl = "https://music.holodex.net/watch/${item.videoId.substringBeforeLast('_')}/${item.videoId.substringAfterLast('_', "0")}",
                    videoId = item.videoId.substringBeforeLast('_'), channelId = item.channelId
                )
            }

            val menuActions = remember(item) {
                ItemMenuActions(
                    onAddToQueue = { videoListViewModel.addVideoOrItsSegmentsToQueue(actions.playbackItem) },
                    onAddToPlaylist = {
                        val unifiedItem = downloadsViewModel.mapDownloadToPlaybackItem(item).toUnifiedDisplayItem()
                        playlistManagementViewModel.prepareItemForPlaylistAddition(unifiedItem)
                    },
                    onShare = { /* Handled internally */ },
                    onDownload = { /* No-op */ },
                    onDelete = actions.onDelete,
                    onGoToVideo = { videoId -> navController.navigate(AppDestinations.videoDetailRoute(videoId)) },
                    onGoToArtist = { channelId -> navController.navigate("channel_details/$channelId") }
                )
            }

            if (item.downloadStatus == DownloadStatus.COMPLETED) {
                ItemOptionsMenu(
                    state = menuState,
                    actions = menuActions,
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                )
            }
        }
    }
}


@Composable
private fun DownloadStatusIndicator(
    status: DownloadStatus,
    progress: Int,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRetryDownload: () -> Unit,
    onRetryExport: () -> Unit,
    onDelete: () -> Unit,
    onShowMenu: () -> Unit
) {
    Box {
        when (status) {
            DownloadStatus.COMPLETED -> {
                IconButton(onClick = onShowMenu) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                }
            }
            DownloadStatus.DOWNLOADING -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (progress > 0) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { progress / 100f }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(text = "$progress", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.action_cancel_download), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            DownloadStatus.ENQUEUED -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.status_queued), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.action_cancel_download), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            DownloadStatus.PAUSED -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_resume_download), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.action_cancel_download), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            DownloadStatus.FAILED -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onRetryDownload) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_retry_download), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            DownloadStatus.EXPORT_FAILED -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onRetryExport) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_retry_download), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            DownloadStatus.NOT_DOWNLOADED, DownloadStatus.DELETING -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            DownloadStatus.PROCESSING -> Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun EmptyDownloadsState(isSearching: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 50.dp)
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Filled.SearchOff else Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = if (isSearching) stringResource(R.string.message_no_search_results_downloads) else stringResource(R.string.message_no_downloads),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}