package com.example.holodex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.domain.action.GlobalMediaActionHandler
import com.example.holodex.ui.composables.ErrorStateWithRetry
import com.example.holodex.ui.composables.LoadingState
import com.example.holodex.ui.composables.SimpleProcessedBackground
import com.example.holodex.ui.composables.UnifiedListItem
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.generateArtworkUrlList
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.VideoDetailsViewModel
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailsScreen(
    navController: NavController,
    onNavigateUp: () -> Unit,
    contentPadding: PaddingValues,
    actionHandler: GlobalMediaActionHandler = hiltViewModel()
) {
    val videoDetailsViewModel: VideoDetailsViewModel = hiltViewModel()
    val favoritesViewModel: FavoritesViewModel = hiltViewModel()

    val videoItem by videoDetailsViewModel.videoItem.collectAsStateWithLifecycle()
    val songItems by videoDetailsViewModel.songItems.collectAsStateWithLifecycle()
    val isLoading by videoDetailsViewModel.isLoading.collectAsStateWithLifecycle()
    val error by videoDetailsViewModel.error.collectAsStateWithLifecycle()
    val transientMessage by videoDetailsViewModel.transientMessage.collectAsStateWithLifecycle()
    val dynamicTheme by videoDetailsViewModel.dynamicTheme.collectAsStateWithLifecycle()
    val favoritesState by favoritesViewModel.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val backgroundImageUrl by remember(videoItem) {
        derivedStateOf { videoItem?.artworkUrls?.firstOrNull() }
    }

    LaunchedEffect(error) {
        error?.let {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
                videoDetailsViewModel.clearError()
            }
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
                title = {
                    Text(
                        text = videoItem?.title ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    videoItem?.let { video ->
                        val isFavorited = favoritesState.likedItemsMap.containsKey(video.channelId)

                        IconButton(onClick = {
                            val channelInfo = ChannelDetails(
                                id = video.channelId,
                                name = video.artistText,
                                englishName = null,
                                description = null,
                                photoUrl = null,
                                bannerUrl = null,
                                org = if(video.isExternal) "External" else null,
                                suborg = null,
                                twitter = null,
                                group = null
                            )
                            favoritesViewModel.toggleFavoriteChannel(channelInfo)
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
            SimpleProcessedBackground(
                artworkUri = backgroundImageUrl,
                dynamicColor = dynamicTheme.primary
            )

            CompositionLocalProvider(LocalContentColor provides dynamicTheme.onPrimary) {
                Box(modifier = Modifier
                    .padding(top = paddingValues.calculateTopPadding())
                    .fillMaxSize()) {

                    when {
                        isLoading && videoItem == null -> {
                            LoadingState(message = stringResource(R.string.loading_content_message))
                        }
                        error != null && videoItem == null -> {
                            ErrorStateWithRetry(
                                message = error!!,
                                onRetry = { }
                            )
                        }
                        videoItem != null -> {
                            VideoDetailsContent(
                                videoItem = videoItem!!,
                                songItems = songItems,
                                videoDetailsViewModel = videoDetailsViewModel,
                                actionHandler = actionHandler,
                                dynamicTheme = dynamicTheme,
                                contentPadding = contentPadding
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
    videoItem: UnifiedDisplayItem,
    songItems: List<UnifiedDisplayItem>,
    videoDetailsViewModel: VideoDetailsViewModel,
    actionHandler: GlobalMediaActionHandler,
    dynamicTheme: DynamicTheme,
    contentPadding: PaddingValues
) {
    val layoutDirection = LocalLayoutDirection.current
    val effectivePadding = PaddingValues(
        top = 0.dp,
        start = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = contentPadding.calculateBottomPadding() + 16.dp
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = effectivePadding
    ) {
        // 1. Header
        item {
            VideoDetailsHeader(
                videoItem = videoItem,
                songCount = songItems.size,
                onPlayAll = { videoDetailsViewModel.playAllSegments() },
                onAddToQueue = { videoDetailsViewModel.addAllSegmentsToQueue() },
                onDownloadAll = { videoDetailsViewModel.downloadAllSegments() },
                dynamicTheme = dynamicTheme
            )
            HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.2f))
        }

        // 2. Song List
        if (songItems.isNotEmpty()) {
            itemsIndexed(
                songItems,
                key = { _, song -> song.stableId }
            ) { index, songItem ->
                UnifiedListItem(
                    item = songItem,
                    actions = actionHandler,
                    enableMarquee = true,
                    onItemClick = {
                        actionHandler.onPlay(songItems, index)
                    }
                )
            }
        } else {
            item {
                EmptyStateMessage()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoDetailsHeader(
    videoItem: UnifiedDisplayItem,
    songCount: Int,
    onPlayAll: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownloadAll: () -> Unit,
    dynamicTheme: DynamicTheme
) {
    // FIX: Generate High-Res artwork list specifically for the Header
    val highResArtworkUrls = remember(videoItem) {
        val tempPlaybackItem = videoItem.toPlaybackItem()
        generateArtworkUrlList(tempPlaybackItem, ThumbnailQuality.MAX)
    }

    var currentUrlIndex by remember(highResArtworkUrls) { mutableIntStateOf(0) }

    Column(modifier = Modifier.padding(16.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(highResArtworkUrls.getOrNull(currentUrlIndex))
                .placeholder(R.drawable.ic_placeholder_image)
                .error(R.drawable.ic_error_image)
                .crossfade(true).build(),
            onError = {
                if (currentUrlIndex < highResArtworkUrls.lastIndex) {
                    currentUrlIndex++
                }
            },
            contentDescription = stringResource(R.string.video_thumbnail_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = videoItem.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = videoItem.artistText,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )

        if (songCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = pluralStringResource(R.plurals.song_count, songCount, songCount),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Spacer(Modifier.height(16.dp))

        if (songCount > 0) {
            // FIX: Pass Color.White to the ActionButtons call
            ActionButtons(
                onPlayAll = onPlayAll,
                onAddToQueue = onAddToQueue,
                onDownloadAll = onDownloadAll,
                dynamicTheme = dynamicTheme,
                forcedContentColor = Color.White
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
    dynamicTheme: DynamicTheme,
    forcedContentColor: Color // FIX: Added this parameter
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = forcedContentColor,
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
            colors = ButtonDefaults.outlinedButtonColors(contentColor = forcedContentColor),
            border = BorderStroke(1.dp, forcedContentColor.copy(alpha = 0.5f))
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_add_to_queue_short))
        }

        IconButton(
            onClick = onDownloadAll,
            colors = IconButtonDefaults.iconButtonColors(contentColor = forcedContentColor)
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