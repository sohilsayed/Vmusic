// File: java/com/example/holodex/ui/screens/ForYouScreen.kt

package com.example.holodex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.R
import com.example.holodex.data.model.discovery.DiscoveryChannel
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.data.model.discovery.SingingStreamShelfItem
import com.example.holodex.ui.composables.CarouselShelf
import com.example.holodex.ui.composables.ChannelCard
import com.example.holodex.ui.composables.ErrorStateWithRetry
import com.example.holodex.ui.composables.LoadingState
import com.example.holodex.ui.composables.PlaylistCard
import com.example.holodex.ui.composables.UnifiedGridItem
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.viewmodel.DiscoveryViewModel
import com.example.holodex.viewmodel.VideoListViewModel
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.state.UiState
import kotlinx.coroutines.flow.collectLatest

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForYouScreen(
    navController: NavController
) {
    val discoveryViewModel: DiscoveryViewModel = hiltViewModel()
    val videoListViewModel: VideoListViewModel = hiltViewModel()

    val forYouState by discoveryViewModel.forYouState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        discoveryViewModel.loadForYouContent()
        discoveryViewModel.transientMessage.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shelf_title_for_you)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
            when (val state = forYouState) {
                is UiState.Loading -> LoadingState(message = "Loading your personalized content...")
                is UiState.Error -> ErrorStateWithRetry(
                    message = state.message,
                    onRetry = { discoveryViewModel.loadForYouContent() })

                is UiState.Success -> {
                    val data = state.data
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item {
                            val uiState = UiState.Success(data.recentSingingStreams ?: emptyList())
                            // --- START OF FIX ---
                            CarouselShelf<SingingStreamShelfItem>(
                                title = stringResource(R.string.shelf_title_recent_streams_favorites),
                                uiState = uiState,
                                actionContent = {
                                    TextButton(onClick = {
                                        videoListViewModel.setBrowseContextAndNavigate(org = "Favorites")
                                        navController.navigate(AppDestinations.HOME_ROUTE)
                                    }) {
                                        Text(stringResource(R.string.action_show_more))
                                    }
                                },
                                itemContent = { item ->
                                    val shell = item.video.toUnifiedDisplayItem(false, emptySet())
                                    UnifiedGridItem(
                                        item = shell,
                                        onClick = { navController.navigate(AppDestinations.videoDetailRoute(item.video.id)) }
                                    )
                                }
                            )
                            // --- END OF FIX ---
                        }

                        item {
                            val radios = remember {
                                data.recommended?.playlists?.filter {
                                    it.type.startsWith("radio")
                                } ?: emptyList()
                            }
                            val uiState = UiState.Success(radios)
                            // --- START OF FIX ---
                            CarouselShelf<PlaylistStub>(
                                title = "Favorite Artist Radios",
                                uiState = uiState,
                                actionContent = {
                                    TextButton(onClick = { /* TODO: Navigate to full list of favorite radios */ }) {
                                        Text(stringResource(R.string.action_show_more))
                                    }
                                },
                                itemContent = { item ->
                                    PlaylistCard(
                                        playlist = item,
                                        onPlaylistClicked = { navController.navigate(AppDestinations.playlistDetailsRoute(it.id)) }
                                    )
                                }
                            )
                            // --- END OF FIX ---
                        }

                        item {
                            val recommendedChannels = remember { data.channels ?: emptyList() }
                            val uiState = UiState.Success(recommendedChannels)
                            // --- START OF FIX ---
                            CarouselShelf<DiscoveryChannel>(
                                title = "Discover More Channels",
                                uiState = uiState,
                                actionContent = {
                                    TextButton(onClick = { /* TODO: Navigate to full list of favorite channels */ }) {
                                        Text(stringResource(R.string.action_show_more))
                                    }
                                },
                                itemContent = { channel ->
                                    ChannelCard(
                                        channel = channel,
                                        onChannelClicked = { channelId -> navController.navigate("channel_details/$channelId") }
                                    )
                                }
                            )
                            // --- END OF FIX ---
                        }
                    }
                }
            }
        }
    }
}