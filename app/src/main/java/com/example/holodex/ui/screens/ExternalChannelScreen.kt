// File: java/com/example/holodex/ui/screens/ExternalChannelScreen.kt
package com.example.holodex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.holodex.R
import com.example.holodex.ui.composables.EmptyState
import com.example.holodex.ui.composables.ErrorStateWithRetry
import com.example.holodex.ui.composables.LoadingState
import com.example.holodex.ui.composables.SimpleProcessedBackground
import com.example.holodex.ui.composables.UnifiedListItem
import com.example.holodex.util.findActivity
import com.example.holodex.viewmodel.ExternalChannelViewModel
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.VideoListViewModel
import com.example.holodex.viewmodel.mappers.toPlaybackItem
import com.example.holodex.viewmodel.state.UiState

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun ExternalChannelScreen(
    navController: NavController,
    onNavigateUp: () -> Unit,
    channelViewModel: ExternalChannelViewModel = hiltViewModel(),
    favoritesViewModel: FavoritesViewModel = hiltViewModel(),
    videoListViewModel: VideoListViewModel = hiltViewModel(findActivity()),
    playlistManagementViewModel: PlaylistManagementViewModel = hiltViewModel(findActivity())
) {
    val details by channelViewModel.channelDetails.collectAsStateWithLifecycle()
    val musicItems by channelViewModel.musicItems.collectAsStateWithLifecycle()
    val uiState by channelViewModel.uiState.collectAsStateWithLifecycle()
    val dynamicTheme by channelViewModel.dynamicTheme.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Collect the new pagination states
    val isLoadingMore by channelViewModel.isLoadingMore.collectAsStateWithLifecycle()
    val endOfList by channelViewModel.endOfList.collectAsStateWithLifecycle()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) return@derivedStateOf false
            val lastVisibleItem = layoutInfo.visibleItemsInfo.last()
            lastVisibleItem.index >= layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            channelViewModel.loadMoreMusic()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { details?.name?.let { Text(it) } },
                navigationIcon = { IconButton(onClick = onNavigateUp) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, navigationIconContentColor = Color.White, titleContentColor = Color.White)
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            SimpleProcessedBackground(artworkUri = details?.photoUrl, dynamicColor = dynamicTheme.primary)

            when (val state = uiState) {
                is UiState.Loading -> LoadingState(message = "Loading music...")
                is UiState.Error -> ErrorStateWithRetry(message = state.message, onRetry = { channelViewModel.loadMoreMusic(true) })
                is UiState.Success -> {
                    if (musicItems.isEmpty()) {
                        EmptyState(message = "No music content found for this channel.", onRefresh = { channelViewModel.loadMoreMusic(true) })
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.padding(paddingValues).fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            item {
                                details?.let { ExternalChannelHeader(details = it) }
                            }
                            items(musicItems, key = { it.stableId }) { item ->
                                UnifiedListItem(
                                    item = item,
                                    onItemClicked = { videoListViewModel.playFavoriteOrLikedSegmentItem(item.toPlaybackItem()) },
                                    navController = navController,
                                    videoListViewModel = videoListViewModel,
                                    favoritesViewModel = favoritesViewModel,
                                    playlistManagementViewModel = playlistManagementViewModel,
                                    isExternal = true
                                )
                            }

                            // *** ADD THE PAGINATION FOOTER UI ***
                            item {
                                if (isLoadingMore) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                    }
                                } else if (endOfList) {
                                    Text(
                                        text = stringResource(R.string.message_youve_reached_the_end),
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalChannelHeader(details: com.example.holodex.data.model.discovery.ChannelDetails) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = details.photoUrl,
            contentDescription = "Channel Avatar",
            modifier = Modifier.size(96.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Text(details.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }
}