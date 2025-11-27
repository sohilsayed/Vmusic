// File: java/com/example/holodex/ui/composables/FullPlayerScreen.kt
package com.example.holodex.ui.composables

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.generateArtworkUrlList
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
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private object FullPlayerDefaults {
    const val VOLUME_SWIPE_SENSITIVITY_FACTOR = 0.005f
    const val HORIZONTAL_SWIPE_THRESHOLD_PX = 50f
    val ArtworkShape = RoundedCornerShape(16.dp)
    const val ARTWORK_ANIMATION_DURATION_MS = 250
    const val VOLUME_SLIDER_AUTO_HIDE_DELAY_MS = 3000L
    const val QUEUE_SHEET_MAX_HEIGHT_FACTOR = 0.7f
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

    // State from ViewModels
    val isRadioMode by playbackViewModel.isRadioModeActive.collectAsStateWithLifecycle()
    val artworkUri by rememberFullPlayerArtworkState(playbackViewModel.uiState)
    val currentItem by rememberFullPlayerCurrentItemState(playbackViewModel.uiState)
    val isLoading by rememberFullPlayerLoadingState(playbackViewModel.uiState)
    val queueInfo by rememberFullPlayerQueueInfoState(playbackViewModel.uiState)
    val (queueItems, currentIndexInQueue, isQueueNotEmpty) = queueInfo
    val uiState by playbackViewModel.uiState.collectAsStateWithLifecycle()
    val repeatMode = uiState.repeatMode
    val shuffleMode = uiState.shuffleMode
    val progress by rememberFullPlayerProgressState(playbackViewModel.uiState)
    val favoritesState by favoritesViewModel.collectAsState()
    val dynamicTheme by fullPlayerViewModel.dynamicTheme.collectAsStateWithLifecycle()

    // Local state
    var showVolumeSlider by remember { mutableStateOf(false) }
    var volumeSliderValue by remember { mutableFloatStateOf(0.7f) }
    var showLyricsView by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // Animation state
    val artworkScale = remember { Animatable(1f) }
    val artworkAlpha = remember { Animatable(1f) }

    // Animated colors based on dynamic theme
    val animatedPrimaryColor by animateColorAsState(
        dynamicTheme.primary,
        label = "animated_primary_color",
        animationSpec = tween(1200)
    )
    val animatedOnPrimaryColor by animateColorAsState(
        dynamicTheme.onPrimary,
        label = "animated_on_primary_color",
        animationSpec = tween(500)
    )

    // Audio and haptic feedback
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val haptic = LocalHapticFeedback.current

    // Computed states
    val isCurrentItemLiked = remember(currentItem, favoritesState.likedItemsMap) {
        currentItem?.let { pbItem ->
            val likeId = pbItem.id
            favoritesState.likedItemsMap.containsKey(likeId)
        } == true
    }

    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Update theme when artwork changes
    LaunchedEffect(artworkUri) {
        fullPlayerViewModel.updateThemeFromArtwork(artworkUri)
    }

    // Artwork transition animations
    LaunchedEffect(currentItem?.id) {
        if (currentItem != null) {
            coroutineScope.launch {
                artworkAlpha.animateTo(0.5f, tween(150))
                artworkAlpha.animateTo(1f, tween(FullPlayerDefaults.ARTWORK_ANIMATION_DURATION_MS, easing = EaseOutCubic))
            }
            coroutineScope.launch {
                artworkScale.animateTo(0.95f, tween(150))
                artworkScale.animateTo(1f, tween(FullPlayerDefaults.ARTWORK_ANIMATION_DURATION_MS, easing = EaseOutCubic))
            }
        }
    }

    // Auto-hide volume slider
    LaunchedEffect(showVolumeSlider) {
        if (showVolumeSlider) {
            delay(FullPlayerDefaults.VOLUME_SLIDER_AUTO_HIDE_DELAY_MS)
            showVolumeSlider = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Background with dynamic theme
        SimpleProcessedBackground(
            artworkUri = artworkUri,
            dynamicColor = dynamicTheme.primary
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = animatedPrimaryColor.copy(alpha = 0.45f)
        ) {}

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            PlayerTopBar(
                isLiked = isCurrentItemLiked,
                isShowingLyrics = showLyricsView,
                queueNotEmpty = isQueueNotEmpty,
                isItemLoaded = currentItem != null,
                iconTint = animatedOnPrimaryColor,
                onNavigateUp = actions.onNavigateUp,
                onLikeToggle = { currentItem?.let { actions.onToggleLike(it) } },
                onQueueClick = { coroutineScope.launch { showQueueSheet = true } },
                onToggleLyrics = { showLyricsView = !showLyricsView },
                actions = actions,
                currentItem = currentItem,
                navController = navController,
                playbackViewModel = playbackViewModel
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showLyricsView) {
                    LyricsView(
                        currentItem = currentItem,
                        textColor = animatedOnPrimaryColor,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                } else {
                    PlayerContent(
                        currentItem = currentItem,
                        trackInfoColor = animatedOnPrimaryColor,
                        isLoading = isLoading,
                        artworkScale = artworkScale.value,
                        artworkAlpha = artworkAlpha.value,
                        onHorizontalSwipe = { dragAmount ->
                            if (dragAmount < -FullPlayerDefaults.HORIZONTAL_SWIPE_THRESHOLD_PX && isQueueNotEmpty) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                actions.onSkipToNext()
                            } else if (dragAmount > FullPlayerDefaults.HORIZONTAL_SWIPE_THRESHOLD_PX && isQueueNotEmpty) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                actions.onSkipToPrevious()
                            }
                        },
                        onVerticalSwipe = { deltaY ->
                            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val newVolume = (currentVolume - deltaY * FullPlayerDefaults.VOLUME_SWIPE_SENSITIVITY_FACTOR * maxVolume)
                                .roundToInt().coerceIn(0, maxVolume)
                            if (newVolume != currentVolume) {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                volumeSliderValue = newVolume.toFloat() / maxVolume
                                if (!showVolumeSlider) showVolumeSlider = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            actions.onTogglePlayPause()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            player?.let { validPlayer ->
                AnimatedVisibility(visible = showVolumeSlider && !showLyricsView) {
                    VolumeSlider(
                        value = volumeSliderValue,
                        onValueChange = {
                            volumeSliderValue = it
                            val newVolumeInt = (it * maxVolume).roundToInt()
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolumeInt, 0)
                        },
                        thumbColor = animatedPrimaryColor,
                        activeTrackColor = animatedOnPrimaryColor
                    )
                }

                Media3PlayerControls(
                    player = validPlayer,
                    progress = progress,
                    shuffleMode = shuffleMode,
                    repeatMode = repeatMode,
                    onSeek = { positionSec -> actions.onSeekTo(positionSec) },
                    onScrubbingChange = { isScrubbing -> playbackViewModel.setScrubbing(isScrubbing) },
                    primaryColor = animatedPrimaryColor,
                    onPrimaryColor = animatedOnPrimaryColor,
                    onPlayPause = actions.onTogglePlayPause,
                    onSkipPrevious = actions.onSkipToPrevious,
                    onSkipNext = actions.onSkipToNext,
                    onToggleShuffle = actions.onToggleShuffleMode,
                    isRadioMode = isRadioMode,
                    onToggleRepeat = actions.onToggleRepeatMode
                )
            }
        }

        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = queueSheetState
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
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_desc_navigate_back), tint = iconTint)
        }
        Spacer(Modifier.weight(1f))

        IconButton(onClick = onToggleLyrics) {
            Icon(
                imageVector = if (isShowingLyrics) Icons.Filled.MusicNote else Icons.Filled.TextFields,
                contentDescription = stringResource(if (isShowingLyrics) R.string.action_hide_lyrics else R.string.action_show_lyrics),
                tint = iconTint
            )
        }

        IconButton(onClick = onLikeToggle, enabled = isItemLoaded) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(if (isLiked) R.string.content_desc_unlike_button else R.string.content_desc_like_button),
                tint = iconTint
            )
        }

        IconButton(onClick = onQueueClick, enabled = queueNotEmpty) {
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.content_desc_view_queue), tint = iconTint)
        }

        Box {
            IconButton(onClick = { showMoreOptionsDropdown = true }, enabled = isItemLoaded) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.content_desc_more_options), tint = iconTint)
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
        val artistChannelId = currentItem?.channelId
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_view_artist)) },
            onClick = {
                if (!artistChannelId.isNullOrBlank()) {
                    actions.onFindArtist(artistChannelId)
                    navController.navigate(AppDestinations.HOME_ROUTE) {
                        popUpTo(navController.graph.startDestinationRoute ?: AppDestinations.HOME_ROUTE) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    }
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
                    else Toast.makeText(context, R.string.error_no_audio_session, Toast.LENGTH_SHORT).show()
                }
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.Filled.Equalizer, null) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerContent(
    modifier: Modifier = Modifier,
    currentItem: PlaybackItem?,
    trackInfoColor: Color,
    isLoading: Boolean,
    artworkScale: Float,
    artworkAlpha: Float,
    onHorizontalSwipe: (dragAmount: Float) -> Unit,
    onVerticalSwipe: (deltaY: Float) -> Unit,
    onDoubleTap: () -> Unit
) {
    val context = LocalContext.current

    // Use onSizeChanged to determine artwork size based on parent container
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    val artworkSize = with(LocalDensity.current) { (parentSize.height * 0.4f).toDp() }

    val artworkUrls = remember(currentItem) {
        generateArtworkUrlList(currentItem, ThumbnailQuality.MAX)
    }
    var currentUrlIndex by remember(artworkUrls) { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .onSizeChanged { parentSize = it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        Spacer(modifier = Modifier.height(artworkSize * 0.1f))
        Box(
            modifier = Modifier
                .size(artworkSize)
                .aspectRatio(1f)
                .clip(FullPlayerDefaults.ArtworkShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale; alpha = artworkAlpha }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onDoubleClick = onDoubleTap,
                    onClick = {}
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount.y.absoluteValue > dragAmount.x.absoluteValue * 1.5) {
                            onVerticalSwipe(dragAmount.y)
                        } else {
                            onHorizontalSwipe(dragAmount.x)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading && currentItem == null) {
                CircularProgressIndicator()
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artworkUrls.getOrNull(currentUrlIndex))
                        .placeholder(R.drawable.ic_default_album_art_placeholder)
                        .error(R.drawable.ic_error_image)
                        .crossfade(true).build(),
                    contentDescription = stringResource(R.string.content_desc_album_art),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { if (currentUrlIndex < artworkUrls.lastIndex) { currentUrlIndex++ } }
                )
            }
        }
        Spacer(modifier = Modifier.height(artworkSize * 0.15f))
        TrackInfo(currentItem, trackInfoColor)
        Spacer(modifier = Modifier.weight(1f))
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
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
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
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 20.sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LyricsView(currentItem: PlaybackItem?, modifier: Modifier = Modifier, textColor: Color) {
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
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
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

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.width(32.dp).height(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    ) {}
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                    modifier = Modifier.fillMaxSize().padding(32.dp),
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
        border = if (isCurrentlyPlaying) BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
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
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
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