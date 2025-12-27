// File: java/com/example/holodex/ui/composables/FullPlayerScreen.kt
package com.example.holodex.ui.composables

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.util.formatDurationSecondsToString
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.util.getHighResArtworkUrl
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.FullPlayerViewModel
import com.example.holodex.viewmodel.PlaybackViewModel
import com.example.holodex.viewmodel.rememberFullPlayerArtworkState
import com.example.holodex.viewmodel.rememberFullPlayerCurrentItemState
import com.example.holodex.viewmodel.rememberFullPlayerLoadingState
import com.example.holodex.viewmodel.rememberFullPlayerProgressState
import com.example.holodex.viewmodel.rememberFullPlayerQueueInfoState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

private object FullPlayerDefaults {
    val ArtworkShape = RoundedCornerShape(12.dp)
    const val VOLUME_SLIDER_AUTO_HIDE_DELAY_MS = 3000L
    const val QUEUE_SHEET_MAX_HEIGHT_FACTOR = 0.6f
}

@Immutable
data class FullPlayerActions(
    val onNavigateUp: () -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onSkipToNext: () -> Unit,
    val onSkipToPrevious: () -> Unit,
    val onToggleRepeatMode: () -> Unit,
    val onToggleShuffleMode: () -> Unit,
    val onPlayQueueItemAtIndex: (Int) -> Unit,
    val onReorderQueueItem: (from: Int, to: Int) -> Unit,
    val onRemoveQueueItem: (index: Int) -> Unit,
    val onClearQueue: () -> Unit,
    val onToggleLike: (PlaybackItem) -> Unit,
    val onFindArtist: (channelId: String) -> Unit,
    val onOpenAudioSettings: (audioSessionId: Int) -> Unit,
    val onAddToPlaylist: (PlaybackItem) -> Unit,
)

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun FullPlayerScreenContent(
    navController: NavHostController,
    player: Player?,
    actions: FullPlayerActions,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // ViewModels
    val fullPlayerViewModel: FullPlayerViewModel = hiltViewModel()
    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    val favoritesViewModel: FavoritesViewModel = hiltViewModel()

    // State Collection
    val uiState by playbackViewModel.uiState.collectAsStateWithLifecycle()
    val isRadioMode by playbackViewModel.isRadioModeActive.collectAsStateWithLifecycle()

    // Derived States (Selectors)
    val artworkUri by rememberFullPlayerArtworkState(playbackViewModel.uiState)
    val currentItem by rememberFullPlayerCurrentItemState(playbackViewModel.uiState)
    val isLoading by rememberFullPlayerLoadingState(playbackViewModel.uiState)
    val progress by rememberFullPlayerProgressState(playbackViewModel.uiState)
    val queueInfo by rememberFullPlayerQueueInfoState(playbackViewModel.uiState)
    val (queueItems, currentIndexInQueue, isQueueNotEmpty) = queueInfo

    val favoritesState by favoritesViewModel.collectAsState()
    val dynamicTheme by fullPlayerViewModel.dynamicTheme.collectAsStateWithLifecycle()

    // Local UI State
    var showVolumeSlider by remember { mutableStateOf(false) }
    var volumeSliderValue by remember { mutableFloatStateOf(0.7f) }
    var showLyricsView by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // System Services
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Logic: Is Liked?
    val isCurrentItemLiked = remember(currentItem, favoritesState.likedItemsMap) {
        currentItem?.let { favoritesState.likedItemsMap.containsKey(it.id) } == true
    }

    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Update Theme on Artwork Change
    LaunchedEffect(artworkUri) {
        fullPlayerViewModel.updateThemeFromArtwork(artworkUri)
    }

    // Auto-hide Volume Slider
    LaunchedEffect(showVolumeSlider) {
        if (showVolumeSlider) {
            delay(FullPlayerDefaults.VOLUME_SLIDER_AUTO_HIDE_DELAY_MS)
            showVolumeSlider = false
        }
    }

    // --- OPTIMIZATION: Low Res Background URL ---
    // We force a 100x100 request for the background blur.
    // Blurring a 100px image is 100x faster than blurring a 1000px image and looks identical.
    val backgroundArtworkUri = remember(artworkUri) {
        if (artworkUri == null) null
        else getHighResArtworkUrl(artworkUri, preferredSizeOverride = "400x400")
    }

    // --- ANIMATION: Breathing Artwork ---
    val isPlaying = uiState.isPlaying
    val artworkScaleAnimation by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.85f, // Shrink when paused
        animationSpec = tween(durationMillis = 500, easing = EaseOutCubic),
        label = "artwork_breathing"
    )
    val artworkElevation by animateFloatAsState(
        targetValue = if (isPlaying) 20f else 4f, // Drop shadow change
        animationSpec = tween(500), label = "elevation"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 1. PERFORMANCE OPTIMIZED BACKGROUND
        SimpleProcessedBackground(
            artworkUri = backgroundArtworkUri, // Use Low-Res
            dynamicColor = dynamicTheme.primary
        )

        // 2. FOREGROUND CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // A. TOP BAR
            PlayerTopBar(
                isLiked = isCurrentItemLiked,
                isShowingLyrics = showLyricsView,
                queueNotEmpty = isQueueNotEmpty,
                isItemLoaded = currentItem != null,
                iconTint = Color.White, // Always white on dark/glassy background
                onNavigateUp = actions.onNavigateUp,
                onLikeToggle = { currentItem?.let { actions.onToggleLike(it) } },
                onQueueClick = { coroutineScope.launch { showQueueSheet = true } },
                onToggleLyrics = { showLyricsView = !showLyricsView },
                actions = actions,
                currentItem = currentItem,
                navController = navController,
                playbackViewModel = playbackViewModel
            )

            // B. MAIN CONTENT (Art + Controls)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // ARTWORK or LYRICS
                if (!showLyricsView) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(
                                horizontal = 36.dp,
                                vertical = 24.dp
                            ), // Increased padding for cleaner look
                        contentAlignment = Alignment.Center
                    ) {
                        // The Shadow & Scaling Container
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = artworkScaleAnimation
                                    scaleY = artworkScaleAnimation
                                    shadowElevation = artworkElevation.dp.toPx()
                                    shape = FullPlayerDefaults.ArtworkShape
                                    clip = true
                                }
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (currentItem != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(artworkUri) // High-Res for Foreground
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = stringResource(R.string.content_desc_album_art),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .align(Alignment.Center),
                                    tint = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                } else {
                    LyricsView(
                        currentItem = currentItem,
                        textColor = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                    )
                }

                // TITLE & ARTIST (Marquee)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentItem?.title ?: stringResource(R.string.loading_track),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee() // Auto-scrolls long titles
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentItem?.artistText ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee() // Auto-scrolls long artists
                    )
                }

                // CONTROLS
                player?.let { validPlayer ->
                    AnimatedVisibility(visible = showVolumeSlider && !showLyricsView) {
                        VolumeSlider(
                            value = volumeSliderValue,
                            onValueChange = {
                                volumeSliderValue = it
                                val newVolumeInt = (it * maxVolume).roundToInt()
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    newVolumeInt,
                                    0
                                )
                            },
                            thumbColor = Color.White,
                            activeTrackColor = Color.White
                        )
                    }

                    Media3PlayerControls(
                        player = validPlayer,
                        // Pass pure state, do not rely on player.* properties for UI logic
                        isPlaying = uiState.isPlaying,
                        isLoading = isLoading,
                        progress = progress,
                        shuffleMode = uiState.shuffleMode,
                        repeatMode = uiState.repeatMode,
                        isRadioMode = isRadioMode,
                        onSeek = { actions.onSeekTo(it) },
                        onScrubbingChange = { playbackViewModel.setScrubbing(it) },
                        onPlayPause = actions.onTogglePlayPause,
                        onSkipPrevious = actions.onSkipToPrevious,
                        onSkipNext = actions.onSkipToNext,
                        onToggleShuffle = actions.onToggleShuffleMode,
                        onToggleRepeat = actions.onToggleRepeatMode,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
        }

        // QUEUE SHEET
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = queueSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                Box(modifier = Modifier.fillMaxHeight(FullPlayerDefaults.QUEUE_SHEET_MAX_HEIGHT_FACTOR)) {
                    QueueSheetContent(
                        queueItems = queueItems,
                        currentIndex = currentIndexInQueue,
                        isRadioMode = isRadioMode,
                        onPlayQueueItem = actions.onPlayQueueItemAtIndex,
                        onClearQueue = {
                            actions.onClearQueue()
                            coroutineScope.launch { queueSheetState.hide(); showQueueSheet = false }
                        },
                        onMoveItem = actions.onReorderQueueItem,
                        onRemoveItem = actions.onRemoveQueueItem
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    isLiked: Boolean,
    isShowingLyrics: Boolean,
    queueNotEmpty: Boolean,
    isItemLoaded: Boolean,
    onNavigateUp: () -> Unit,
    onLikeToggle: () -> Unit,
    onQueueClick: () -> Unit,
    onToggleLyrics: () -> Unit,
    iconTint: Color,
    actions: FullPlayerActions,
    currentItem: PlaybackItem?,
    navController: NavHostController,
    playbackViewModel: PlaybackViewModel
) {
    var showMoreOptionsDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateUp) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.content_desc_navigate_back),
                tint = iconTint
            )
        }
        Spacer(Modifier.weight(1f))

        IconButton(onClick = onToggleLyrics) {
            Icon(
                imageVector = if (isShowingLyrics) Icons.Filled.MusicNote else Icons.Filled.TextFields,
                contentDescription = "Lyrics",
                tint = iconTint.copy(alpha = if (isShowingLyrics) 1f else 0.7f)
            )
        }

        IconButton(onClick = onLikeToggle, enabled = isItemLoaded) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Like",
                tint = if (isLiked) Color(0xFFE91E63) else iconTint // Pink for like
            )
        }

        IconButton(onClick = onQueueClick, enabled = queueNotEmpty) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                stringResource(R.string.content_desc_view_queue),
                tint = iconTint
            )
        }

        Box {
            IconButton(onClick = { showMoreOptionsDropdown = true }, enabled = isItemLoaded) {
                Icon(
                    Icons.Filled.MoreVert,
                    stringResource(R.string.content_desc_more_options),
                    tint = iconTint
                )
            }
            PlayerOverflowMenu(
                expanded = showMoreOptionsDropdown,
                onDismissRequest = { showMoreOptionsDropdown = false },
                currentItem = currentItem,
                actions = actions,
                navController = navController,
                playbackViewModel = playbackViewModel
            )
        }
    }
}

@Composable
private fun PlayerOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    currentItem: PlaybackItem?,
    actions: FullPlayerActions,
    navController: NavHostController,
    playbackViewModel: PlaybackViewModel
) {
    val context = LocalContext.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_add_to_playlist_menu)) },
            onClick = {
                currentItem?.let { actions.onAddToPlaylist(it) }
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
            enabled = currentItem != null
        )

        // --- NEW OPTION: GO TO VIDEO ---
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_view_video)) },
            onClick = {
                currentItem?.let { item ->
                    navController.navigate(AppDestinations.videoDetailRoute(item.videoId))
                    actions.onNavigateUp() // Close the Full Player sheet
                }
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.Filled.Movie, null) },
            enabled = currentItem != null
        )
        // -------------------------------

        val artistChannelId = currentItem?.channelId
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_view_artist)) },
            onClick = {
                if (!artistChannelId.isNullOrBlank()) {
                    actions.onFindArtist(artistChannelId)
                    navController.navigate(AppDestinations.HOME_ROUTE) {
                        popUpTo(
                            navController.graph.startDestinationRoute ?: AppDestinations.HOME_ROUTE
                        ) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    }
                    actions.onNavigateUp() // Close the Full Player sheet
                }
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.Filled.PersonSearch, null) },
            enabled = !artistChannelId.isNullOrBlank()
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_audio_settings)) },
            onClick = {
                playbackViewModel.getAudioSessionId()?.let {
                    if (it != 0) actions.onOpenAudioSettings(it)
                    else Toast.makeText(
                        context,
                        R.string.error_no_audio_session,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.Filled.Equalizer, null) }
        )
    }
}

@Composable
private fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    thumbColor: Color,
    activeTrackColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            value < 0.01f -> Icons.AutoMirrored.Filled.VolumeMute
            value < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
            else -> Icons.AutoMirrored.Filled.VolumeUp
        }
        Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.content_desc_volume),
            modifier = Modifier.size(24.dp),
            tint = activeTrackColor
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = activeTrackColor.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun TrackInfo(currentItem: PlaybackItem?, textColor: Color) {
    AnimatedContent(
        targetState = currentItem?.id ?: "loading",
        transitionSpec = { fadeIn(tween(220, 90)) togetherWith fadeOut(tween(90)) },
        label = "trackInfoAnimation"
    ) { targetId ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = if (targetId == "loading" || currentItem == null) stringResource(R.string.loading_track) else currentItem.title,
                color = textColor,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (targetId == "loading" || currentItem == null) "" else currentItem.artistText,
                color = textColor.copy(alpha = 0.8f),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    lineHeight = 20.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LyricsView(
    currentItem: PlaybackItem?,
    modifier: Modifier = Modifier,
    textColor: Color
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (currentItem?.description.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.lyrics_not_available),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = currentItem.description,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheetContent(
    queueItems: List<PlaybackItem>,
    currentIndex: Int,
    isRadioMode: Boolean,
    onPlayQueueItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onMoveItem: (from: Int, to: Int) -> Unit,
    onRemoveItem: (index: Int) -> Unit
) {
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to -> onMoveItem(from.index, to.index) }
    )

    LaunchedEffect(currentIndex) {
        if (currentIndex in 0..queueItems.lastIndex) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    ) {}
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.up_next_queue_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (queueItems.isNotEmpty()) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.queue_items_count,
                                    queueItems.size,
                                    queueItems.size
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = queueItems.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        FilledTonalIconButton(
                            onClick = onClearQueue,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                Icons.Filled.PlaylistRemove,
                                contentDescription = stringResource(R.string.action_clear_queue)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        AnimatedContent(
            targetState = queueItems.isEmpty(),
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(
                    animationSpec = tween(300)
                )
            },
            label = "queue_content"
        ) { isEmpty ->
            if (isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.QueueMusic,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = stringResource(R.string.empty_queue),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.empty_queue_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = queueItems,
                        key = { index, item -> "${item.id}_$index" }
                    ) { index, itemData ->
                        ReorderableItem(
                            state = reorderableState,
                            key = "${itemData.id}_$index",
                            enabled = !isRadioMode
                        ) { isDragging ->
                            QueueItemRow(
                                item = itemData,
                                index = index + 1,
                                isCurrentlyPlaying = index == currentIndex,
                                isDragging = isDragging,
                                isRadioMode = isRadioMode,
                                onItemClick = { onPlayQueueItem(index) },
                                onRemoveClick = { onRemoveItem(index) },
                                modifier = Modifier.longPressDraggableHandle()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    item: PlaybackItem,
    index: Int,
    isCurrentlyPlaying: Boolean,
    isDragging: Boolean,
    isRadioMode: Boolean,
    onItemClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = tween(200),
        label = "drag_elevation"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isCurrentlyPlaying -> MaterialTheme.colorScheme.primaryContainer
            isDragging -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(200),
        label = "container_color"
    )

    Surface(
        onClick = onItemClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        shadowElevation = elevation,
        border = if (isCurrentlyPlaying) BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Index/Play indicator
            Surface(
                shape = CircleShape,
                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCurrentlyPlaying) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Artwork
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.artworkUri)
                    .placeholder(R.drawable.ic_default_album_art_placeholder)
                    .error(R.drawable.ic_error_image)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            // Title and artist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrentlyPlaying) FontWeight.Medium else FontWeight.Normal
                )
                Text(
                    text = item.artistText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(
                        alpha = 0.8f
                    ) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            Text(
                text = formatDurationSecondsToString(item.durationSec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Remove button
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_remove_from_queue),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Drag handle (only show in non-radio mode)
            if (!isRadioMode) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.drag_to_reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDragging) 1f else 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}