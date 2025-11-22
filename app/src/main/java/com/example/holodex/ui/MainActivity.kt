// File: java/com/example/holodex/ui/MainActivity.kt
@file:OptIn(ExperimentalMaterial3Api::class)

@file:Suppress("OPT_IN_USAGE") // Optional: To silence the false warning for UnstableApi
@file:androidx.media3.common.util.UnstableApi

package com.example.holodex.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.holodex.MyApp
import com.example.holodex.R
import com.example.holodex.auth.LoginScreen
import com.example.holodex.data.db.LikedItemType
import com.example.holodex.playback.PlaybackRequestManager
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.service.ARG_PLAYBACK_ITEMS_LIST
import com.example.holodex.service.ARG_SHOULD_SHUFFLE
import com.example.holodex.service.ARG_START_INDEX
import com.example.holodex.service.ARG_START_POSITION_SEC
import com.example.holodex.service.CUSTOM_COMMAND_PREPARE_FROM_REQUEST
import com.example.holodex.service.MediaPlaybackService
import com.example.holodex.ui.composables.FullPlayerActions
import com.example.holodex.ui.composables.FullPlayerScreenContent
import com.example.holodex.ui.composables.MiniPlayerWithProgressBar
import com.example.holodex.ui.dialogs.CreatePlaylistDialog
import com.example.holodex.ui.dialogs.SelectPlaylistDialog
import com.example.holodex.ui.screens.ChannelScreen
import com.example.holodex.ui.screens.DiscoveryScreen
import com.example.holodex.ui.screens.DownloadsScreen
import com.example.holodex.ui.screens.ExternalChannelScreen
import com.example.holodex.ui.screens.ForYouScreen
import com.example.holodex.ui.screens.FullListViewScreen
import com.example.holodex.ui.screens.HomeScreen
import com.example.holodex.ui.screens.LibraryScreen
import com.example.holodex.ui.screens.PlaylistDetailsScreen
import com.example.holodex.ui.screens.SettingsScreen
import com.example.holodex.ui.screens.VideoDetailsScreen
import com.example.holodex.ui.screens.navigation.BottomNavItem
import com.example.holodex.ui.theme.HolodexMusicTheme
import com.example.holodex.viewmodel.ChannelDetailsViewModel
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.FullListViewModel
import com.example.holodex.viewmodel.PlaybackViewModel
import com.example.holodex.viewmodel.PlaylistDetailsViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.SettingsViewModel
import com.example.holodex.viewmodel.VideoDetailsViewModel
import com.example.holodex.viewmodel.VideoListViewModel
import com.example.holodex.viewmodel.VideoListViewModel.MusicCategoryType
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

object AppDestinations {
    const val HOME_ROUTE = "home"
    const val DISCOVERY_ROUTE = "discover"
    const val LIBRARY_ROUTE = "library"
    const val DOWNLOADS_ROUTE = "downloads"
    const val SETTINGS_ROUTE = "settings"
    const val FULL_PLAYER_ROUTE = "full_player_dialog"

    const val VIDEO_DETAILS_ROUTE_TEMPLATE = "video_details/{${VideoDetailsViewModel.VIDEO_ID_ARG}}"
    fun videoDetailRoute(videoId: String) = "video_details/$videoId"

    const val LOGIN_ROUTE = "login"

    const val FULL_LIST_VIEW_ROUTE_TEMPLATE =
        "full_list/{${FullListViewModel.CATEGORY_TYPE_ARG}}/{${FullListViewModel.ORG_ARG}}"

    fun fullListViewRoute(category: MusicCategoryType, org: String): String {
        val encodedOrg = URLEncoder.encode(org, StandardCharsets.UTF_8.toString())
        return "full_list/${category.name}/$encodedOrg"
    }

    const val FOR_YOU_ROUTE = "for_you"

    const val PLAYLIST_DETAILS_ROUTE_TEMPLATE =
        "playlist_details/{${PlaylistDetailsViewModel.PLAYLIST_ID_ARG}}"

    fun playlistDetailsRoute(playlistId: String): String {
        val encodedId = URLEncoder.encode(playlistId, StandardCharsets.UTF_8.toString())
        return "playlist_details/$encodedId"
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playbackRequestManager: PlaybackRequestManager

    @Inject
    lateinit var player: Player

    private val myApp: MyApp by lazy { application as MyApp }
    private var mediaController: MediaController? = null
    private lateinit var sessionToken: SessionToken

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Timber.d("Permission [${it.key}] granted: ${it.value}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSplashScreen()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        sessionToken = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
        checkAndRequestPermissions()

        setContent {
            val navController = rememberNavController()
            HolodexApp(
                navController = navController, // Pass the controller
                playbackRequestManager = playbackRequestManager,
                activity = this,
                player = player

            )
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Storage permission for Android 9 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // ... (onStart, onStop, sendPlaybackRequestToService, etc. remain the same)
    override fun onStart() {
        super.onStart()
        connectMediaController()

        // NEW: Trigger reconciliation whenever the app becomes visible
        Timber.d("MainActivity onStart: Triggering download reconciliation.")
        myApp.reconcileCompletedDownloads()
    }

    override fun onStop() {
        super.onStop()
        // Don't release MediaController in onStop - only disconnect if needed
        // The MediaController should persist across onStop/onStart cycles
        Timber.tag(TAG).d("MainActivity onStop - keeping MediaController connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only release MediaController when the activity is actually being destroyed
        mediaController?.release()
        mediaController = null
        Timber.tag(TAG).d("MediaController released in onDestroy")
    }

    internal fun sendPlaybackRequestToService(
        items: List<PlaybackItem>,
        startIndex: Int,
        startPositionSec: Long,
        shouldShuffle: Boolean = false
    ) {
        if (items.isEmpty()) {
            Timber.tag(TAG)
                .w("sendPlaybackRequestToService called with empty item list. Aborting."); return
        }
        val serviceIntent = Intent(this, MediaPlaybackService::class.java)
        try {
            items.forEach { item ->
                if (item.streamUri?.startsWith("content://") == true) {
                    val uri = item.streamUri!!.toUri()
                    grantUriPermission(
                        "com.example.holodex",
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    Timber.d("Granted READ permission for URI: $uri")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to grant URI permissions.")
            // Optionally show a toast if permission granting fails, as playback will likely fail.
        }
        // --- END OF FIX ---

        startService(serviceIntent)
        Timber.tag(TAG).d("MediaPlaybackService explicitly started/ensured running.")
        if (mediaController == null || !mediaController!!.isConnected) {
            Timber.tag(TAG)
                .w("MediaController not available/connected. Attempting to connect then send.")
            connectMediaController { success ->
                if (success) {
                    sendPlaybackRequestToService(
                        items,
                        startIndex,
                        startPositionSec,
                        shouldShuffle
                    )
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.error_player_service_not_ready),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }; return
        }
        val commandArgs = Bundle().apply {
            putParcelableArrayList(ARG_PLAYBACK_ITEMS_LIST, ArrayList(items))
            putInt(ARG_START_INDEX, startIndex)
            putLong(ARG_START_POSITION_SEC, startPositionSec)
            putBoolean(ARG_SHOULD_SHUFFLE, shouldShuffle)
        }
        val command = SessionCommand(CUSTOM_COMMAND_PREPARE_FROM_REQUEST, Bundle.EMPTY)
        Timber.tag(TAG)
            .d("Sending $CUSTOM_COMMAND_PREPARE_FROM_REQUEST with ${items.size} items, startIndex: $startIndex, shuffle: $shouldShuffle.")
        val resultFuture: ListenableFuture<SessionResult> =
            mediaController!!.sendCustomCommand(command, commandArgs)
        resultFuture.addListener({
            try {
                val result: SessionResult = resultFuture.get()
                if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                    Timber.tag(TAG)
                        .w("Custom command $CUSTOM_COMMAND_PREPARE_FROM_REQUEST failed: ${result.resultCode}, msg: ${result.sessionError?.message}")
                    Toast.makeText(
                        this,
                        getString(R.string.error_playback_command_failed, result.resultCode),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Timber.tag(TAG)
                        .i("Custom command $CUSTOM_COMMAND_PREPARE_FROM_REQUEST successful.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG)
                    .e(e, "Error processing result of $CUSTOM_COMMAND_PREPARE_FROM_REQUEST")
                Toast.makeText(
                    this,
                    getString(R.string.error_initiating_playback),
                    Toast.LENGTH_LONG
                ).show()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun connectMediaController(onConnected: ((Boolean) -> Unit)? = null) {
        if (mediaController?.isConnected == true) {
            Timber.tag(TAG).d("MediaController already connected.")
            onConnected?.invoke(true)
            return
        }

        // Clean up any existing controller that might be in a bad state
        if (mediaController != null && !mediaController!!.isConnected) {
            Timber.tag(TAG).d("Cleaning up disconnected MediaController")
            try {
                mediaController?.release()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Error releasing old MediaController")
            }
            mediaController = null
        }

        Timber.tag(TAG).d("Attempting to connect MediaController.")

        // Ensure service is started before attempting connection
        val serviceIntent = Intent(this, MediaPlaybackService::class.java)
        startService(serviceIntent)

        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                Timber.tag(TAG).d("MediaController connected successfully")
                onConnected?.invoke(true)
            } catch (e: Exception) {
                mediaController = null
                Timber.tag(TAG).e(e, "Error connecting MediaController")
                onConnected?.invoke(false)
            }
        }, MoreExecutors.directExecutor())
    }
}

@SuppressLint("UnstableApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolodexApp(
    navController: NavHostController,
    playbackRequestManager: PlaybackRequestManager,
    activity: ComponentActivity,
    player: Player?
) {
    val coroutineScope = rememberCoroutineScope()
    var showFullPlayerSheet by remember { mutableStateOf(false) }
    val fullPlayerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ViewModels for globally-scoped UI state (theme, player, dialogs) are hoisted here.
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    val playlistManagementViewModel: PlaylistManagementViewModel = hiltViewModel(activity)
    val videoListViewModel: VideoListViewModel = hiltViewModel(activity)

    val playbackUiState by playbackViewModel.uiState.collectAsStateWithLifecycle()
    val isPlayerActive = playbackUiState.currentItem != null
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navigationRequest by videoListViewModel.navigationRequest.collectAsStateWithLifecycle()

    LaunchedEffect(navigationRequest) {
        navigationRequest?.let { destination ->
            // --- LOGGING POINT: This should now appear ---
            Timber.d("HolodexApp: NavigationRequest observed: $destination")
            when (destination) {
                is VideoListViewModel.NavigationDestination.VideoDetails -> {
                    navController.navigate(AppDestinations.videoDetailRoute(destination.videoId))
                }

                is VideoListViewModel.NavigationDestination.HomeScreenWithSearch -> {
                    // This can be used to navigate home and pop up the search bar
                    navController.navigate(AppDestinations.HOME_ROUTE) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                    }
                    // A mechanism to activate search would be needed here, e.g., via the ViewModel
                    videoListViewModel.setSearchActive(true)
                }
            }
            // Important: Consume the navigation event so it doesn't trigger again on recomposition
            videoListViewModel.clearNavigationRequest()
        }
    }

    LaunchedEffect(playbackRequestManager) {
        playbackRequestManager.playbackRequest.collectLatest { request ->
            (activity as MainActivity).sendPlaybackRequestToService(
                request.items, request.startIndex, request.startPositionSec, request.shouldShuffle
            )
        }
    }

    HolodexMusicTheme(settingsViewModel = settingsViewModel) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            bottomBar = {
                Column {
                    if (isPlayerActive) {
                        MiniPlayerWithProgressBar(
                            playbackViewModel = playbackViewModel,
                            onTapped = { showFullPlayerSheet = true }
                        )
                    }
                    val navItems = listOf(
                        BottomNavItem.Discover,
                        BottomNavItem.Browse,
                        BottomNavItem.Library,
                        BottomNavItem.Downloads
                    )
                    NavigationBar {
                        navItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        item.icon,
                                        contentDescription = stringResource(item.titleResId)
                                    )
                                },
                                label = { Text(stringResource(item.titleResId)) },
                                selected = isSelected,
                                onClick = {
                                    // SPECIAL HANDLING for start destination
                                    if (item.route == AppDestinations.DISCOVERY_ROUTE &&
                                        navController.graph.startDestinationId == navController.graph.findNode(
                                            AppDestinations.DISCOVERY_ROUTE
                                        )?.id
                                    ) {

                                        // Force navigation to start destination by clearing stack
                                        navController.navigate(item.route) {
                                            popUpTo(0) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        // Normal navigation for other destinations
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
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestinations.LIBRARY_ROUTE,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                composable(AppDestinations.DISCOVERY_ROUTE) { DiscoveryScreen(navController = navController) }
                composable(AppDestinations.FOR_YOU_ROUTE) { ForYouScreen(navController = navController) }
                composable(AppDestinations.HOME_ROUTE) {
                    val currentApiKey by settingsViewModel.currentApiKey.collectAsStateWithLifecycle()
                    if (currentApiKey.isBlank()) {
                        ApiKeyMissingContent(navController = navController)
                    } else {
                        HomeScreen(
                            navController = navController,
                            videoListViewModel = videoListViewModel,
                            playlistManagementViewModel = playlistManagementViewModel // <-- PASS IT HERE
                        )
                    }
                }
                composable(
                    route = "external_channel_details/{${ChannelDetailsViewModel.CHANNEL_ID_ARG}}",
                    arguments = listOf(navArgument(ChannelDetailsViewModel.CHANNEL_ID_ARG) {
                        type = NavType.StringType
                    })
                ) {
                    ExternalChannelScreen(
                        navController = navController,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "channel_details/{${ChannelDetailsViewModel.CHANNEL_ID_ARG}}",
                    arguments = listOf(navArgument(ChannelDetailsViewModel.CHANNEL_ID_ARG) {
                        type = NavType.StringType
                    })
                ) {
                    ChannelScreen(
                        navController = navController,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable(AppDestinations.LIBRARY_ROUTE) {
                    LibraryScreen(
                        navController = navController,
                        playlistManagementViewModel = playlistManagementViewModel // <-- PASS IT HERE
                    )
                }
                composable(AppDestinations.DOWNLOADS_ROUTE) {
                    DownloadsScreen(
                        navController = navController,
                        playlistManagementViewModel = playlistManagementViewModel // <-- PASS IT HERE
                    )
                }
                composable(AppDestinations.SETTINGS_ROUTE) {
                    val videoListViewModel: VideoListViewModel = hiltViewModel(activity)
                    SettingsScreen(
                        navController = navController,
                        onNavigateUp = { navController.popBackStack() },
                        onApiKeySavedRestartNeeded = { videoListViewModel.refreshCurrentListViaPull() }
                    )
                }
                composable(AppDestinations.LOGIN_ROUTE) { LoginScreen(onLoginSuccess = { navController.popBackStack() }) }
                composable(
                    route = AppDestinations.FULL_LIST_VIEW_ROUTE_TEMPLATE,
                    arguments = listOf(
                        navArgument(FullListViewModel.CATEGORY_TYPE_ARG) {
                            type = NavType.StringType
                        },
                        navArgument(FullListViewModel.ORG_ARG) { type = NavType.StringType }
                    )
                ) {
                    FullListViewScreen(
                        navController = navController,
                        categoryType = MusicCategoryType.valueOf(
                            it.arguments?.getString(
                                FullListViewModel.CATEGORY_TYPE_ARG
                            ) ?: MusicCategoryType.TRENDING.name
                        )
                    )
                }
                composable(
                    AppDestinations.PLAYLIST_DETAILS_ROUTE_TEMPLATE,
                    arguments = listOf(navArgument(PlaylistDetailsViewModel.PLAYLIST_ID_ARG) {
                        type = NavType.StringType
                    })
                ) {
                    PlaylistDetailsScreen(
                        navController = navController,
                        playlistManagementViewModel = playlistManagementViewModel, // <-- PASS IT HERE
                        onNavigateUp = { navController.popBackStack() })
                }
                composable(
                    AppDestinations.VIDEO_DETAILS_ROUTE_TEMPLATE,
                    arguments = listOf(navArgument(VideoDetailsViewModel.VIDEO_ID_ARG) {
                        type = NavType.StringType
                    })
                ) {
                    VideoDetailsScreen(
                        navController = navController,
                        onNavigateUp = { navController.popBackStack() })
                }
            }
        }
    }

    if (showFullPlayerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFullPlayerSheet = false },
            sheetState = fullPlayerSheetState,
            containerColor = Color.Transparent,
            shape = RoundedCornerShape(0),
            scrimColor = Color.Transparent,
            dragHandle = null
        ) {
            FullPlayerScreenDestination(
                player = player,
                navController = navController,
                onNavigateUp = {
                    coroutineScope.launch { fullPlayerSheetState.hide() }.invokeOnCompletion {
                        if (!fullPlayerSheetState.isVisible) {
                            showFullPlayerSheet = false
                        }
                    }
                }
            )
        }
    }

    val showSelectPlaylistDialog by playlistManagementViewModel.showSelectPlaylistDialog.collectAsStateWithLifecycle()
    val userPlaylistsForDialog by playlistManagementViewModel.userPlaylists.collectAsStateWithLifecycle()
    val showCreatePlaylistDialog by playlistManagementViewModel.showCreatePlaylistDialog.collectAsStateWithLifecycle()

    if (showSelectPlaylistDialog) {
        SelectPlaylistDialog(
            playlists = userPlaylistsForDialog,
            onDismissRequest = { playlistManagementViewModel.cancelAddToPlaylistFlow() },
            onPlaylistSelected = { playlist ->
                playlistManagementViewModel.addItemToExistingPlaylist(
                    playlist
                )
            },
            onCreateNewPlaylistClicked = { playlistManagementViewModel.handleCreateNewPlaylistFromSelectionDialog() }
        )
    }
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismissRequest = { playlistManagementViewModel.cancelAddToPlaylistFlow() },
            onCreatePlaylist = { name, desc ->
                playlistManagementViewModel.confirmCreatePlaylist(
                    name,
                    desc
                )
            }
        )
    }
}

@Composable
private fun FullPlayerScreenDestination(
    player: Player?,
    navController: NavHostController, // FIX: Accept navController
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    val favoritesViewModel: FavoritesViewModel = hiltViewModel()
    val playlistManagementViewModel: PlaylistManagementViewModel = hiltViewModel()
    val videoListViewModel: VideoListViewModel = hiltViewModel()

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
            onFindArtist = { channelId -> videoListViewModel.performSearchByChannelId(channelId) },
            onOpenAudioSettings = { audioSessionId -> Timber.d("Action: Open Audio Settings for session ID: $audioSessionId") },
            onAddToPlaylist = { playbackItem ->

                playlistManagementViewModel.prepareItemForPlaylistAdditionFromPlaybackItem(playbackItem)
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

@Composable
private fun ApiKeyMissingContent(
    navController: NavController,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.status_api_key_required_main),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            navController.navigate(AppDestinations.SETTINGS_ROUTE) {
                launchSingleTop = true
            }
        }) { Text(stringResource(R.string.button_go_to_settings)) }
    }
}