package com.example.holodex.ui

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.holodex.ui.composables.FullPlayerActions
import com.example.holodex.ui.composables.FullPlayerScreenContent
import com.example.holodex.ui.composables.MainScreenLayout
import com.example.holodex.ui.composables.MiniPlayerWithProgressBar
import com.example.holodex.ui.composables.PlaylistManagementDialogs
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.ui.navigation.HolodexNavHost
import com.example.holodex.ui.screens.navigation.BottomNavItem
import com.example.holodex.ui.theme.HolodexMusicTheme
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.PlaybackViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.SettingsViewModel
import com.example.holodex.viewmodel.VideoListSideEffect
import com.example.holodex.viewmodel.VideoListViewModel
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectSideEffect

private const val TAG = "MainScreenScaffold"

@SuppressLint("UnstableApi")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenScaffold(
    navController: NavHostController,
    activity: ComponentActivity,
    player: ExoPlayer
) {

    Log.d(TAG, "MainScreenScaffold: Composing")

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // ViewModels
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    val playlistManagementViewModel: PlaylistManagementViewModel = hiltViewModel(activity)
    val videoListViewModel: VideoListViewModel = hiltViewModel(activity)

    Log.d(TAG, "MainScreenScaffold: ViewModels created")

    // UI State
    var showFullPlayerSheet by remember { mutableStateOf(false) }
    val fullPlayerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Navigation State
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // --- Orbit Side Effects ---
    videoListViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is VideoListSideEffect.NavigateTo -> {
                when (val destination = sideEffect.destination) {
                    is VideoListViewModel.NavigationDestination.VideoDetails -> {
                        navController.navigate(AppDestinations.videoDetailRoute(destination.videoId))
                    }

                    is VideoListViewModel.NavigationDestination.HomeScreenWithSearch -> {
                        navController.navigate(AppDestinations.HOME_ROUTE) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                        }
                        videoListViewModel.setSearchActive(true)
                    }
                }
            }

            is VideoListSideEffect.ShowToast -> {
                Toast.makeText(context, sideEffect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }



    HolodexMusicTheme(settingsViewModel = settingsViewModel) {
        MainScreenLayout(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Column {
                    // The MiniPlayer logic remains the same
                    MiniPlayerWithProgressBar(
                        playbackViewModel = playbackViewModel,
                        onTapped = { showFullPlayerSheet = true }
                    )
                    NavigationBar {
                        val navItems = listOf(
                            BottomNavItem.Discover,
                            BottomNavItem.Browse,
                            BottomNavItem.Library,
                            BottomNavItem.Downloads
                        )
                        navItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.titleResId)) },
                                selected = isSelected,
                                onClick = {
                                    if (item.route == AppDestinations.DISCOVERY_ROUTE &&
                                        navController.graph.startDestinationId == navController.graph.findNode(
                                            AppDestinations.DISCOVERY_ROUTE
                                        )?.id
                                    ) {
                                        navController.navigate(item.route) {
                                            popUpTo(0) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Log.d(TAG, "bottomBar Column: NavigationBar composed")
                }
            }
        ) { dynamicPadding ->
            // dynamicPadding now contains the EXACT height of (MiniPlayer + NavBar)

            Box(modifier = Modifier.fillMaxSize()) {
                HolodexNavHost(
                    navController = navController,
                    videoListViewModel = videoListViewModel,
                    playlistManagementViewModel = playlistManagementViewModel,
                    activity = activity,
                    // Pass this padding down to your screens!
                    contentPadding = dynamicPadding
                )
            }
        }

        if (showFullPlayerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFullPlayerSheet = false },
                sheetState = fullPlayerSheetState,
                containerColor = Color.Transparent,
                shape = RoundedCornerShape(0),
                scrimColor = Color.Black.copy(alpha = 0.6f),
                dragHandle = null
            ) {
                FullPlayerScreenDestination(
                    player = player,
                    navController = navController,
                    onNavigateUp = {
                        coroutineScope.launch { fullPlayerSheetState.hide() }.invokeOnCompletion {
                            if (!fullPlayerSheetState.isVisible) showFullPlayerSheet = false
                        }
                    }
                )
            }
        }

        PlaylistManagementDialogs(playlistManagementViewModel)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun FullPlayerScreenDestination(
    player: Player?,
    navController: NavHostController,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    val favoritesViewModel: FavoritesViewModel = hiltViewModel()
    val playlistManagementViewModel: PlaylistManagementViewModel = hiltViewModel()
    val videoListViewModel: VideoListViewModel = hiltViewModel()
    val context = LocalContext.current

    val playerActions = remember(
        playbackViewModel,
        favoritesViewModel,
        videoListViewModel,
        playlistManagementViewModel
    ) {
        FullPlayerActions(
            onNavigateUp = onNavigateUp,
            onTogglePlayPause = { playbackViewModel.togglePlayPause() },
            onSeekTo = { positionSec -> playbackViewModel.seekTo(positionSec) },
            onSkipToNext = { playbackViewModel.skipToNext() },
            onSkipToPrevious = { playbackViewModel.skipToPrevious() },
            onToggleRepeatMode = { playbackViewModel.toggleRepeatMode() },
            onToggleShuffleMode = { playbackViewModel.toggleShuffleMode() },
            onPlayQueueItemAtIndex = { index -> playbackViewModel.playQueueItemAtIndex(index) },
            onReorderQueueItem = { from, to -> playbackViewModel.reorderQueueItem(from, to) },
            onRemoveQueueItem = { index -> playbackViewModel.removeItemFromQueue(index) },
            onClearQueue = { playbackViewModel.clearCurrentQueue() },
            onToggleLike = { playbackItem -> favoritesViewModel.toggleLike(playbackItem) },
            onFindArtist = { channelId -> videoListViewModel.setBrowseContextAndNavigate(channelId = channelId) },
            onOpenAudioSettings = { audioSessionId ->
                Toast.makeText(context, "Audio FX Session ID: $audioSessionId", Toast.LENGTH_SHORT)
                    .show()
            },
            onAddToPlaylist = { playbackItem ->
                playlistManagementViewModel.prepareItemForPlaylistAdditionFromPlaybackItem(
                    playbackItem
                )
            }
        )
    }

    FullPlayerScreenContent(
        player = player,
        navController = navController,
        actions = playerActions,
        modifier = modifier
    )
}