package com.example.holodex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.data.db.ExternalChannelEntity
import com.example.holodex.data.db.FavoriteChannelEntity
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.model.discovery.DiscoveryChannel
import com.example.holodex.data.model.discovery.SingingStreamShelfItem
import com.example.holodex.ui.composables.CarouselShelf
import com.example.holodex.ui.composables.ChannelCard
import com.example.holodex.ui.composables.SimpleProcessedBackground
import com.example.holodex.ui.composables.UnifiedGridItem
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.findActivity
import com.example.holodex.util.getYouTubeThumbnailUrl
import com.example.holodex.viewmodel.ChannelDetailsViewModel
import com.example.holodex.viewmodel.DiscoveryViewModel
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.MusicCategoryType
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.VideoListViewModel
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.state.UiState
import org.orbitmvi.orbit.compose.collectAsState

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    navController: NavController,
    onNavigateUp: () -> Unit
) {
    val channelViewModel: ChannelDetailsViewModel = hiltViewModel()
    val favoritesViewModel: FavoritesViewModel = hiltViewModel()
    val discoveryViewModel: DiscoveryViewModel = hiltViewModel()
    val videoListViewModel: VideoListViewModel = hiltViewModel(findActivity())

    val detailsState by channelViewModel.channelDetailsState.collectAsStateWithLifecycle()
    val discoveryState by channelViewModel.discoveryState.collectAsStateWithLifecycle()
    val popularSongsState by channelViewModel.popularSongsState.collectAsStateWithLifecycle()
    val favoritesState by favoritesViewModel.collectAsState()
    val dynamicTheme by channelViewModel.dynamicTheme.collectAsStateWithLifecycle()

    val backgroundImageUrl by remember(discoveryState, detailsState) {
        derivedStateOf {
            val detailsData = (detailsState as? UiState.Success)?.data
            detailsData?.bannerUrl?.takeIf { it.isNotBlank() }
                ?: (discoveryState as? UiState.Success)?.data?.recentSingingStreams?.firstOrNull()?.video?.id?.let {
                    getYouTubeThumbnailUrl(it, ThumbnailQuality.MAX).firstOrNull()
                }
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        SimpleProcessedBackground(
            artworkUri = backgroundImageUrl,
            dynamicColor = dynamicTheme.primary
        )

        Scaffold(
            topBar = { TopAppBar(title = {}, navigationIcon = { IconButton(onClick = onNavigateUp) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) },
            containerColor = Color.Transparent
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.padding(paddingValues).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    when(val state = detailsState) {
                        is UiState.Success -> ChannelHeader(
                            details = state.data,
                            isFavorited = favoritesState.favoriteChannels.any {
                                (it is FavoriteChannelEntity && it.id == state.data.id) ||
                                        (it is ExternalChannelEntity && it.channelId == state.data.id)
                            },
                            onFavoriteClicked = { favoritesViewModel.toggleFavoriteChannelByDetails(state.data) }
                        )
                        is UiState.Loading -> Box(modifier = Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        is UiState.Error -> Text(text = state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                }

                item {
                    CarouselShelf<UnifiedDisplayItem>(
                        title = "Popular",
                        uiState = popularSongsState,
                        actionContent = {
                            TextButton(onClick = { navController.navigate(AppDestinations.fullListViewRoute(MusicCategoryType.TRENDING, channelViewModel.channelId)) }) {
                                Text(stringResource(id = R.string.action_show_more))
                            }
                        },
                        itemContent = { item ->
                            UnifiedGridItem(item = item, onClick = { discoveryViewModel.playUnifiedItem(item) })
                        }
                    )
                }
                item {
                    val recentStreamsUiState: UiState<List<SingingStreamShelfItem>> =
                        remember(discoveryState) {
                            when (val state = discoveryState) {
                                is UiState.Success -> UiState.Success(
                                    state.data.recentSingingStreams ?: emptyList()
                                )

                                is UiState.Error -> UiState.Error(state.message)
                                is UiState.Loading -> UiState.Loading
                            }
                        }


                    CarouselShelf<SingingStreamShelfItem>(
                        title = "Latest Streams",
                        uiState = recentStreamsUiState,
                        actionContent = {
                            TextButton(onClick = {
                                videoListViewModel.setBrowseContextAndNavigate(channelId = channelViewModel.channelId)
                                navController.navigate(AppDestinations.HOME_ROUTE)
                            }) {
                                Text(stringResource(id = R.string.action_show_more))
                            }
                        },
                        itemContent = { item ->
                            val shell = item.video.toUnifiedDisplayItem(false, emptySet())
                            UnifiedGridItem(
                                item = shell,
                                onClick = {
                                    navController.navigate(
                                        AppDestinations.videoDetailRoute(item.video.id)
                                    )
                                })
                        }
                    )
                }

                item {
                    val otherChannelsUiState: UiState<List<DiscoveryChannel>> = remember(discoveryState) {
                        when (val state = discoveryState) {
                            is UiState.Success -> UiState.Success(state.data.channels ?: emptyList())
                            is UiState.Error -> UiState.Error(state.message)
                            is UiState.Loading -> UiState.Loading
                        }
                    }
                    val orgName = (detailsState as? UiState.Success)?.data?.org ?: "Organization"


                    CarouselShelf<DiscoveryChannel>(
                        title = "Discover More from $orgName",
                        uiState = otherChannelsUiState,
                        actionContent = {
                            TextButton(onClick = { /* TODO: Navigate to a full channels list for that org */ }) {
                                Text(stringResource(id = R.string.action_show_more))
                            }
                        },
                        itemContent = { channel ->
                            ChannelCard(channel = channel, onChannelClicked = { navController.navigate("channel_details/${channel.id}") })
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    details: ChannelDetails,
    isFavorited: Boolean,
    onFavoriteClicked: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(details.photoUrl).crossfade(true).build(),
            contentDescription = "Channel Avatar",
            modifier = Modifier.size(96.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(details.englishName ?: details.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            details.org?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onFavoriteClicked) {
                Icon(if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (isFavorited) "Favorited" else "Favorite")
            }
            OutlinedButton(onClick = { uriHandler.openUri("https://youtube.com/channel/${details.id}") }) {
                Icon(painterResource(R.drawable.youtube), null)
            }
            details.twitter?.let {
                OutlinedButton(onClick = { uriHandler.openUri("https://twitter.com/${it}") }) {
                    Icon(painterResource(R.drawable.twitter), null)
                }
            }
        }
    }
}