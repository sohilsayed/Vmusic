package com.example.holodex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.R
import com.example.holodex.ui.composables.EmptyState
import com.example.holodex.ui.composables.UnifiedGridItem
import com.example.holodex.ui.composables.UnifiedListItem
import com.example.holodex.viewmodel.DownloadsSideEffect
import com.example.holodex.viewmodel.DownloadsViewModel
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.VideoListViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navController: NavController,
    downloadsViewModel: DownloadsViewModel = hiltViewModel(),
    playlistManagementViewModel: PlaylistManagementViewModel,
    contentPadding: PaddingValues // Added
) {
    val state by downloadsViewModel.collectAsState()
    val context = LocalContext.current
    val videoListViewModel: VideoListViewModel = hiltViewModel()
    val favoritesViewModel: FavoritesViewModel = hiltViewModel()

    downloadsViewModel.collectSideEffect { effect ->
        when (effect) {
            is DownloadsSideEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
        }
    }

    var isGridView by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bottom_nav_downloads)) },
                actions = {
                    if (state.items.isNotEmpty()) {
                        TextButton(onClick = { downloadsViewModel.playAllDownloadsShuffled() }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.action_play_all))
                        }
                    }
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->

        // Merge padding
        val layoutDirection = LocalLayoutDirection.current

        val unifiedPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection)
        )

        PullToRefreshBox(
            isRefreshing = false, // Unified Repo updates automatically
            onRefresh = { /* No-op, list updates via Flow */ },
            modifier = Modifier.padding(top = unifiedPadding.calculateTopPadding()).fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { downloadsViewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_your_downloads_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { downloadsViewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Filled.Clear, stringResource(R.string.action_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )

                if (state.items.isEmpty()) {
                    // Reusing EmptyState for consistency
                    EmptyState(
                        message = if (state.searchQuery.isNotEmpty()) stringResource(R.string.message_no_search_results_downloads)
                        else stringResource(R.string.message_no_downloads),
                        onRefresh = {}
                    )
                } else {
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = unifiedPadding.calculateBottomPadding()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.items, key = { it.stableId }) { item ->
                                UnifiedGridItem(
                                    item = item,
                                    onClick = { downloadsViewModel.playDownloads(item) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = unifiedPadding.calculateBottomPadding())
                        ) {
                            items(state.items, key = { it.stableId }) { item ->
                                UnifiedListItem(
                                    item = item,
                                    onItemClicked = { downloadsViewModel.playDownloads(item) },
                                    navController = navController,
                                    videoListViewModel = videoListViewModel,
                                    favoritesViewModel = favoritesViewModel,
                                    playlistManagementViewModel = playlistManagementViewModel,
                                    isEditing = false, // View mode
                                    onRemoveClicked = { /* Not in edit mode */ }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}