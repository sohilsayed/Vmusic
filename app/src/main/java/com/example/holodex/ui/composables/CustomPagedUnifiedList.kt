@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.example.holodex.ui.composables

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.R
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.VideoListViewModel
import timber.log.Timber

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class) // Fixed: Removed duplicate annotation
@Composable
fun CustomPagedUnifiedList(
    listKeyPrefix: String,
    items: List<UnifiedDisplayItem>,
    listState: LazyListState,
    onItemClicked: (UnifiedDisplayItem) -> Unit,
    videoListViewModel: VideoListViewModel,
    favoritesViewModel: FavoritesViewModel,
    playlistManagementViewModel: PlaylistManagementViewModel,
    navController: NavController,
    isLoadingMore: Boolean,
    endOfList: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 80.dp),
    header: (@Composable () -> Unit)? = null,
) {
    // Track initial load state to fix scroll-to-end bug
    var hasPerformedInitialScroll by remember { mutableStateOf(false) }

    // Fix for scroll-to-end bug: Ensure we start at top after initial load
    LaunchedEffect(items.size) {
        if (items.isNotEmpty() && !hasPerformedInitialScroll) {
            // Only scroll to top if we're not already there (avoids unnecessary animation)
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.scrollToItem(0)
            }
            hasPerformedInitialScroll = true
        } else if (items.isEmpty()) {
            // Reset flag if list becomes empty (e.g., after refresh)
            hasPerformedInitialScroll = false
        }
    }

    // Improved load-more logic with better performance and stability
    val shouldLoadMore by remember {
        derivedStateOf {
            // Early exit conditions for better performance
            if (isLoadingMore || endOfList || items.isEmpty()) {
                false
            } else {
                val layoutInfo = listState.layoutInfo
                val visibleItemsInfo = layoutInfo.visibleItemsInfo

                // More robust check
                if (visibleItemsInfo.isEmpty()) {
                    false
                } else {
                    val lastVisibleItem = visibleItemsInfo.last()
                    val threshold = 3
                    val totalItems = layoutInfo.totalItemsCount

                    // Account for header in total count if present
                    val adjustedTotalItems = if (header != null) totalItems - 1 else totalItems

                    lastVisibleItem.index >= adjustedTotalItems - 1 - threshold
                }
            }
        }
    }

    // More efficient LaunchedEffect that only triggers when actually needed
    LaunchedEffect(shouldLoadMore, isLoadingMore, endOfList) {
        if (shouldLoadMore && !isLoadingMore && !endOfList) {
            Timber.i("CustomPagedUnifiedList ($listKeyPrefix): >>> LOAD MORE UI TRIGGERED <<<")
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            // Reset initial scroll flag on refresh
            hasPerformedInitialScroll = false
            onRefresh()
        },
        modifier = modifier
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(), // Removed redundant modifier parameter
            contentPadding = contentPadding
        ) {
            header?.let {
                item(key = "${listKeyPrefix}_header") { it() }
            }

            items(
                items = items,
                key = { item -> item.stableId }
            ) { item ->
                UnifiedListItem(
                    item = item,
                    onItemClicked = { onItemClicked(item) }, // Fixed formatting
                    videoListViewModel = videoListViewModel,
                    favoritesViewModel = favoritesViewModel,
                    playlistManagementViewModel = playlistManagementViewModel,
                    navController = navController,
                )
            }

            item(key = "${listKeyPrefix}_footer") {
                if (isLoadingMore) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (endOfList && items.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.message_youve_reached_the_end),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp), // Fixed chaining
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}