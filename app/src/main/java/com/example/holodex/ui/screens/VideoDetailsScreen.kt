// File: java/com/example/holodex/ui/screens/VideoDetailsScreen.kt
// File: java/com/example/holodex/ui/screens/VideoDetailsScreen.kt
@file:kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.holodex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.ui.composables.ErrorStateWithRetry
import com.example.holodex.ui.composables.LoadingState
import com.example.holodex.ui.composables.SimpleProcessedBackground
import com.example.holodex.ui.composables.UnifiedListItem
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.findActivity
import com.example.holodex.util.getYouTubeThumbnailUrl
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.VideoDetailsViewModel
import com.example.holodex.viewmodel.VideoListViewModel
import org.orbitmvi.orbit.compose.collectAsState

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailsScreen(
    navController: NavController,
    onNavigateUp: () -> Unit,
) {
    val videoDetailsViewModel: VideoDetailsViewModel = hiltViewModel()
    // *** THE FIX: Get the activity-scoped ViewModel here ***
    val videoListViewModel: VideoListViewModel = hiltViewModel(findActivity())
    val favoritesViewModel: FavoritesViewModel = hiltViewModel()
    val playlistManagementViewModel: PlaylistManagementViewModel = hiltViewModel(findActivity())

    // Call the initialize function once when the screen is first composed.
    LaunchedEffect(Unit) {
        videoDetailsViewModel.initialize(videoListViewModel)
    }

    val videoDetails by videoDetailsViewModel.videoDetails.collectAsStateWithLifecycle()
    val favoritesState by favoritesViewModel.collectAsState()
    val isLoading by videoDetailsViewModel.isLoading.collectAsStateWithLifecycle()
    val error by videoDetailsViewModel.error.collectAsStateWithLifecycle()
    val transientMessage by videoDetailsViewModel.transientMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val dynamicTheme by videoDetailsViewModel.dynamicTheme.collectAsStateWithLifecycle()

    val backgroundImageUrl by remember(videoDetails) {
        derivedStateOf { videoDetails?.id?.let { getYouTubeThumbnailUrl(it, ThumbnailQuality.MAX).firstOrNull() } }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
            videoDetailsViewModel.clearError()
        }
    }
    LaunchedEffect(transientMessage) {
        transientMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            videoDetailsViewModel.clearTransientMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = videoDetails?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    videoDetails?.let { video ->
                        // *** FIX 1: Check Unified Favorites Map ***
                        // The channel ID is the key in the map.
                        val channelId = video.channel.id ?: ""
                        val isFavorited = favoritesState.likedItemsMap.containsKey(channelId)

                        IconButton(onClick = {
                            // *** FIX 2: Call the overload that accepts HolodexVideoItem ***
                            // (We already added this overload in FavoritesViewModel previously)
                            favoritesViewModel.toggleFavoriteChannel(video)
                        }) {
                            Icon(
                                imageVector = if (isFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite Channel",
                                tint = if (isFavorited) dynamicTheme.primary else dynamicTheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = dynamicTheme.onPrimary,
                    navigationIconContentColor = dynamicTheme.onPrimary,
                    actionIconContentColor = dynamicTheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            SimpleProcessedBackground(artworkUri = backgroundImageUrl, dynamicColor = dynamicTheme.primary)
            CompositionLocalProvider(LocalContentColor provides dynamicTheme.onPrimary) {
                Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                    when {
                        isLoading && videoDetails == null -> LoadingState(message = stringResource(R.string.loading_content_message))
                        error != null && videoDetails == null -> ErrorStateWithRetry(
                            message = error!!,
                            onRetry = { videoDetailsViewModel.initialize(videoListViewModel) } // Use initialize for retry
                        )
                        videoDetails != null -> {
                            VideoDetailsContent(
                                videoDetailsViewModel = videoDetailsViewModel,
                                navController = navController,
                                playlistManagementViewModel = playlistManagementViewModel,
                                dynamicTheme = dynamicTheme
                            )
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun VideoDetailsContent(
    navController: NavController,
    videoDetailsViewModel: VideoDetailsViewModel,
    playlistManagementViewModel: PlaylistManagementViewModel,
    dynamicTheme: DynamicTheme
) {
    val videoItem by videoDetailsViewModel.videoDetails.collectAsStateWithLifecycle()
    val songItems by videoDetailsViewModel.unifiedSongItems.collectAsStateWithLifecycle()

    val videoListViewModel: VideoListViewModel = hiltViewModel(findActivity())
    val favoritesViewModel: FavoritesViewModel = hiltViewModel()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        item {
            videoItem?.let {
                VideoDetailsHeader(
                    videoItem = it,
                    songCount = songItems.size,
                    onPlayAll = { videoDetailsViewModel.playAllSegments() },
                    onAddToQueue = { videoDetailsViewModel.addAllSegmentsToQueue() },
                    onDownloadAll = { videoDetailsViewModel.downloadAllSegments() },
                    dynamicTheme = dynamicTheme
                )
            }
            HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.2f))
        }

        if (songItems.isNotEmpty()) {
            itemsIndexed(
                songItems,
                key = { _, song -> song.stableId }
            ) { index, songItem ->
                UnifiedListItem(
                    item = songItem,
                    onItemClicked = { videoDetailsViewModel.playSegment(index) },
                    navController = navController,
                    videoListViewModel = videoListViewModel,
                    favoritesViewModel = favoritesViewModel,
                    playlistManagementViewModel = playlistManagementViewModel
                )
            }
        } else {
            // Display empty state only if the parent video has finished loading
            if (videoItem != null && !videoDetailsViewModel.isLoading.value) {
                item { EmptyStateMessage() }
            }
        }
    }
}

@Composable
private fun VideoDetailsHeader(
    videoItem: HolodexVideoItem,
    songCount: Int,
    onPlayAll: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownloadAll: () -> Unit,
    dynamicTheme: DynamicTheme
) {
    val thumbnailUrls = remember(videoItem.id) {
        getYouTubeThumbnailUrl(videoItem.id, ThumbnailQuality.MAX)
    }
    var currentUrlIndex by remember(thumbnailUrls) { mutableIntStateOf(0) }

    Column(modifier = Modifier.padding(16.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnailUrls.getOrNull(currentUrlIndex))
                .placeholder(R.drawable.ic_placeholder_image)
                .error(R.drawable.ic_error_image)
                .crossfade(true).build(),
            onError = { if (currentUrlIndex < thumbnailUrls.lastIndex) currentUrlIndex++ },
            contentDescription = stringResource(R.string.video_thumbnail_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
        )
        Spacer(Modifier.height(16.dp))
        Text(videoItem.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(videoItem.channel.name, style = MaterialTheme.typography.titleMedium)
        if (songCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = pluralStringResource(R.plurals.song_count, songCount, songCount),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(16.dp))
        if (songCount > 0) {
            ActionButtons(
                onPlayAll = onPlayAll,
                onAddToQueue = onAddToQueue,
                onDownloadAll = onDownloadAll,
                dynamicTheme = dynamicTheme
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ActionButtons(
    onPlayAll: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownloadAll: () -> Unit,
    dynamicTheme: DynamicTheme
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = dynamicTheme.onPrimary,
                contentColor = dynamicTheme.primary
            )
        ) {
            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_play_all))
        }
        OutlinedButton(
            onClick = onAddToQueue,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = dynamicTheme.onPrimary),
            border = BorderStroke(1.dp, dynamicTheme.onPrimary.copy(alpha = 0.5f))
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_add_to_queue_short))
        }
        IconButton(
            onClick = onDownloadAll,
            colors = IconButtonDefaults.iconButtonColors(contentColor = dynamicTheme.onPrimary)
        ) {
            Icon(Icons.Filled.Download, stringResource(R.string.action_download_all))
        }
    }
}


@Composable
private fun EmptyStateMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp), contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.no_song_segments_available),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}