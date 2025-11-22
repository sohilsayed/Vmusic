// File: java/com/example/holodex/ui/screens/FullListViewScreen.kt

package com.example.holodex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.R
import com.example.holodex.data.model.discovery.DiscoveryChannel
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.ui.AppDestinations
import com.example.holodex.ui.composables.ChannelCard
import com.example.holodex.ui.composables.PlaylistCard
import com.example.holodex.ui.composables.UnifiedGridItem
import com.example.holodex.viewmodel.DiscoveryViewModel
import com.example.holodex.viewmodel.FullListViewModel
import com.example.holodex.viewmodel.SubOrgHeader
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.VideoListViewModel.MusicCategoryType

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullListViewScreen(
    navController: NavController,
    categoryType: MusicCategoryType
) {
    val fullListViewModel: FullListViewModel = hiltViewModel()
    val discoveryViewModel: DiscoveryViewModel = hiltViewModel()

    val listState by fullListViewModel.listState.items.collectAsStateWithLifecycle()
    // --- START OF IMPLEMENTATION (1/3) ---
    val isLoadingMore by fullListViewModel.listState.isLoadingMore.collectAsStateWithLifecycle()
    val endOfList by fullListViewModel.listState.endOfList.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // This derived state will be true when the user scrolls near the end of the list.
    // It's a performant way to create a signal for loading more data.
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false

            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false

            // Trigger when the last visible item is within 10 items of the end
            lastVisibleItem.index >= totalItems - 10
        }
    }

    // This effect is triggered whenever the `shouldLoadMore` signal becomes true.
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore && !endOfList) {
            fullListViewModel.loadMore()
        }
    }
    // --- END OF IMPLEMENTATION (1/3) ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            // --- START OF IMPLEMENTATION (2/3) ---
            state = gridState, // Assign the state to the grid
            // --- END OF IMPLEMENTATION (2/3) ---
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                count = listState.size,
                key = { index ->
                    val item = listState[index]
                    when (item) {
                        is UnifiedDisplayItem -> item.stableId
                        is PlaylistStub -> item.id
                        is DiscoveryChannel -> item.id
                        is SubOrgHeader -> item.name
                        else -> item.hashCode()
                    }
                },
                span = { index ->
                    val item = listState[index]
                    if (item is SubOrgHeader) {
                        GridItemSpan(maxLineSpan)
                    } else {
                        GridItemSpan(1)
                    }
                }
            ) { index ->
                val item = listState[index]
                when (item) {
                    is UnifiedDisplayItem -> UnifiedGridItem(item = item, onClick = { discoveryViewModel.playUnifiedItem(item) })
                    is PlaylistStub -> PlaylistCard(playlist = item, onPlaylistClicked = { playlistStub ->
                        navController.navigate(AppDestinations.playlistDetailsRoute(playlistStub.id))})
                    is DiscoveryChannel -> ChannelCard(channel = item, onChannelClicked = { channelId ->
                        navController.navigate("channel_details/$channelId")
                    })
                    is SubOrgHeader -> Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp, top = if (index == 0) 0.dp else 16.dp)
                    )
                }
            }

            // --- START OF IMPLEMENTATION (3/3) ---
            // Add a footer item to show the loading indicator when fetching the next page.
            if (isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }
            }
            // --- END OF IMPLEMENTATION (3/3) ---
        }
    }
}