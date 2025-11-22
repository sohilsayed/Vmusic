package com.example.holodex.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.R
import com.example.holodex.data.model.discovery.DiscoveryChannel
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.ui.composables.ChannelCard
import com.example.holodex.ui.composables.PlaylistCard
import com.example.holodex.ui.composables.UnifiedGridItem
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.viewmodel.DiscoveryViewModel
import com.example.holodex.viewmodel.FullListSideEffect
import com.example.holodex.viewmodel.FullListViewModel
import com.example.holodex.viewmodel.MusicCategoryType
import com.example.holodex.viewmodel.SubOrgHeader
import com.example.holodex.viewmodel.UnifiedDisplayItem
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullListViewScreen(
    navController: NavController,
    categoryType: MusicCategoryType
) {
    val fullListViewModel: FullListViewModel = hiltViewModel()
    val discoveryViewModel: DiscoveryViewModel = hiltViewModel()
    val context = LocalContext.current

    // Collect State
    val state by fullListViewModel.collectAsState()

    // Handle Side Effects
    fullListViewModel.collectSideEffect { effect ->
        when (effect) {
            is FullListSideEffect.ShowToast -> {
                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val gridState = rememberLazyGridState()

    // Pagination Logic
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisibleItem =
                layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= totalItems - 10
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !state.isLoadingMore && !state.endOfList) {
            fullListViewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        categoryType.name.replace('_', ' ').lowercase()
                            .replaceFirstChar { it.uppercase() })
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val items = state.items
            items(
                count = items.size,
                key = { index ->
                    val item = items[index]
                    when (item) {
                        is UnifiedDisplayItem -> item.stableId
                        is PlaylistStub -> item.id
                        is DiscoveryChannel -> item.id
                        is SubOrgHeader -> item.name
                        else -> item.hashCode()
                    }
                },
                span = { index ->
                    if (items[index] is SubOrgHeader) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                }
            ) { index ->
                when (val item = items[index]) {
                    is UnifiedDisplayItem -> UnifiedGridItem(
                        item = item,
                        onClick = { discoveryViewModel.playUnifiedItem(item) })

                    is PlaylistStub -> PlaylistCard(
                        playlist = item,
                        onPlaylistClicked = { playlistStub ->
                            navController.navigate(AppDestinations.playlistDetailsRoute(playlistStub.id))
                        })

                    is DiscoveryChannel -> ChannelCard(
                        channel = item,
                        onChannelClicked = { channelId ->
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

            if (state.isLoadingMore) {
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
        }
    }
}