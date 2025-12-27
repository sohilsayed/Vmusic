package com.example.holodex.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.R
import com.example.holodex.domain.action.GlobalMediaActionHandler
import com.example.holodex.ui.composables.CustomPagedUnifiedList
import com.example.holodex.ui.composables.EmptyState
import com.example.holodex.ui.composables.LoadingSkeleton
import com.example.holodex.ui.composables.sheets.BrowseFiltersSheet
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.viewmodel.MusicCategoryType
import com.example.holodex.viewmodel.VideoListSideEffect
import com.example.holodex.viewmodel.VideoListViewModel
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    videoListViewModel: VideoListViewModel,
    contentPadding: PaddingValues,
    actionHandler: GlobalMediaActionHandler = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var showFilterSheet by remember { mutableStateOf(false) }

    // --- ORBIT STATE COLLECTION ---
    val state by videoListViewModel.collectAsState()

    // --- ORBIT SIDE EFFECTS ---
    videoListViewModel.collectSideEffect { effect ->
        when (effect) {
            is VideoListSideEffect.ShowToast -> {
                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
            is VideoListSideEffect.NavigateTo -> {
                when (val destination = effect.destination) {
                    is VideoListViewModel.NavigationDestination.VideoDetails -> {
                        navController.navigate(AppDestinations.videoDetailRoute(destination.videoId))
                    }
                    is VideoListViewModel.NavigationDestination.HomeScreenWithSearch -> {
                        // Already on Home Screen, logic handled by state update
                    }
                }
            }
        }
    }

    val searchHistory = state.searchHistory

    BackHandler(enabled = state.isSearchActive) {
        videoListViewModel.clearSearchAndReturnToBrowse()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 5 } }
                AnimatedVisibility(
                    visible = showFab && !state.isSearchActive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding())
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.scroll_to_top))
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->

            val topPadding = innerPadding.calculateTopPadding() + 72.dp

            val unifiedPadding = PaddingValues(
                top = topPadding,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
                start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = innerPadding.calculateEndPadding(LocalLayoutDirection.current)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (state.activeContextType == MusicCategoryType.SEARCH) {
                    // --- SEARCH CONTENT ---
                    if (state.searchIsLoadingInitial && state.searchItems.isEmpty()) {
                        LoadingSkeleton(modifier = Modifier.fillMaxSize().padding(top = 80.dp))
                    } else if (state.searchItems.isEmpty() && state.searchEndOfList && !state.searchIsLoadingInitial) {
                        EmptyState(
                            message = stringResource(R.string.status_search_no_results, state.currentSearchQuery),
                            onRefresh = { videoListViewModel.refreshCurrentListViaPull() },
                            modifier = Modifier.padding(top = 80.dp)
                        )
                    } else {
                        CustomPagedUnifiedList(
                            listKeyPrefix = "home_search",
                            items = state.searchItems,
                            listState = listState,
                            actions = actionHandler,
                            onItemClicked = { item ->
                                if (item.isSegment) {

                                    videoListViewModel.onVideoClicked(item)
                                } else {
                                    videoListViewModel.onVideoClicked(item)
                                }
                            },
                            isLoadingMore = state.searchIsLoadingInitial,
                            endOfList = state.searchEndOfList,
                            onLoadMore = { videoListViewModel.loadMore(MusicCategoryType.SEARCH) },
                            isRefreshing = false,
                            onRefresh = {},
                            contentPadding = unifiedPadding
                        )
                    }
                } else {
                    // --- BROWSE CONTENT ---
                    if (state.browseIsLoadingInitial && state.browseItems.isEmpty()) {
                        LoadingSkeleton(modifier = Modifier.fillMaxSize().padding(top = 80.dp))
                    } else {
                        CustomPagedUnifiedList(
                            listKeyPrefix = "home_browse",
                            items = state.browseItems,
                            listState = listState,
                            actions = actionHandler,
                            onItemClicked = { item ->
                                if (item.isSegment) {
                                    videoListViewModel.onVideoClicked(item)
                                } else {
                                    videoListViewModel.onVideoClicked(item)
                                }
                            },
                            isLoadingMore = state.browseIsLoadingMore,
                            endOfList = state.browseEndOfList,
                            onLoadMore = { videoListViewModel.loadMore(state.activeContextType) },
                            isRefreshing = state.browseIsRefreshing,
                            onRefresh = { videoListViewModel.refreshCurrentListViaPull() },
                            contentPadding = unifiedPadding
                        )
                    }
                }
            }
        }

        // Search Bar (Overlay)
        DockedSearchBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding() // <--- CRITICAL FIX FOR FULL SCREEN
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            query = state.currentSearchQuery,
            onQueryChange = { query -> videoListViewModel.onSearchQueryChange(query) },
            onSearch = { query -> videoListViewModel.performSearch(query) },
            active = state.isSearchActive,
            onActiveChange = { active -> videoListViewModel.setSearchActive(active) },
            placeholder = {
                Text(
                    stringResource(R.string.search_your_music_hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                if (state.isSearchActive) {
                    IconButton(onClick = { videoListViewModel.clearSearchAndReturnToBrowse() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                } else {
                    Icon(Icons.Filled.Search, "Search Icon")
                }
            },
            trailingIcon = {
                Row {
                    if (state.isSearchActive && state.currentSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { videoListViewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, stringResource(R.string.action_clear_search))
                        }
                    }
                    if (!state.isSearchActive) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            BadgedBox(badge = {
                                if (state.browseFilterState.hasActiveFilters) {
                                    Badge { Text(state.browseFilterState.activeFilterCount.toString()) }
                                }
                            }) {
                                Icon(Icons.Filled.Tune, stringResource(R.string.action_filter_sort))
                            }
                        }
                        IconButton(onClick = { navController.navigate(AppDestinations.SETTINGS_ROUTE) }) {
                            Icon(Icons.Filled.Settings, stringResource(R.string.settings_title))
                        }
                    }
                }
            }
        ) {
            SearchHistoryList(
                searchHistory = searchHistory,
                onHistoryItemClick = { query ->
                    videoListViewModel.onSearchQueryChange(query)
                    videoListViewModel.performSearch(query)
                }
            )
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            BrowseFiltersSheet(
                initialFilters = state.browseFilterState,
                onFiltersApplied = { newFilters ->
                    showFilterSheet = false
                    videoListViewModel.updateBrowseFilters(newFilters)
                },
                onDismiss = { showFilterSheet = false },
                videoListViewModel = videoListViewModel
            )
        }
    }
}

@Composable
private fun SearchHistoryList(
    searchHistory: List<String>,
    onHistoryItemClick: (String) -> Unit
) {
    if (searchHistory.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp), contentAlignment = Alignment.Center
        ) {
            Text(
                "No recent searches",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = stringResource(R.string.search_history_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        items(searchHistory) { query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHistoryItemClick(query) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.History, null, modifier = Modifier.padding(end = 16.dp))
                Text(query, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}