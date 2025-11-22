// File: java/com/example/holodex/ui/screens/LibraryScreen.kt
package com.example.holodex.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.holodex.R
import com.example.holodex.ui.AppDestinations
import com.example.holodex.ui.dialogs.AddExternalChannelDialog
import com.example.holodex.viewmodel.ExternalChannelViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import kotlinx.coroutines.launch

private enum class LibraryTab(val titleRes: Int) {
    PLAYLISTS(R.string.bottom_nav_playlists),
    FAVORITES(R.string.bottom_nav_favorites),
    HISTORY(R.string.screen_title_history)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    playlistManagementViewModel: PlaylistManagementViewModel,
) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { LibraryTab.entries.size })
    val coroutineScope = rememberCoroutineScope()
    var isGridView by remember { mutableStateOf(false) }

    val externalChannelViewModel: ExternalChannelViewModel = hiltViewModel()
    val showAddChannelDialog by externalChannelViewModel.showDialog.collectAsStateWithLifecycle()

    if (showAddChannelDialog) {
        AddExternalChannelDialog(onDismissRequest = { externalChannelViewModel.closeDialog() })
    }

    Scaffold(
        topBar = {
            LibraryTopAppBar(
                isGridView = isGridView,
                onViewToggle = { isGridView = !isGridView }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { externalChannelViewModel.openDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "Add External Channel")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedCustomTabRow(
                selectedTabIndex = pagerState.currentPage,
                tabs = LibraryTab.entries.map { stringResource(it.titleRes) },
                pagerState = pagerState,
                onTabSelected = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                }
            )

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (LibraryTab.entries[page]) {
                    LibraryTab.FAVORITES -> FavoritesTab(
                        isGridView = isGridView,
                        navController = navController,
                        playlistManagementViewModel = playlistManagementViewModel
                    )
                    LibraryTab.PLAYLISTS -> PlaylistsTab(
                        onPlaylistClicked = { playlist ->
                            val idToNavigate = when {
                                playlist.playlistId < 0 && playlist.serverId == null -> playlist.playlistId.toString()
                                playlist.playlistId > 0 -> playlist.playlistId.toString()
                                else -> playlist.serverId
                            }
                            if (!idToNavigate.isNullOrBlank()) {
                                navController.navigate(AppDestinations.playlistDetailsRoute(idToNavigate))
                            }
                        }
                    )
                    LibraryTab.HISTORY -> HistoryTab(
                        navController = navController,
                        playlistManagementViewModel = playlistManagementViewModel
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopAppBar(
    isGridView: Boolean,
    onViewToggle: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.bottom_nav_library),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            IconButton(onClick = onViewToggle) {
                Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = stringResource(if (isGridView) R.string.action_view_as_list else R.string.action_view_as_grid)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimatedCustomTabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    pagerState: PagerState,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    indicatorColor: Color = MaterialTheme.colorScheme.primary
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val tabWidth = this@BoxWithConstraints.maxWidth / tabs.size
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                )
        )
        val targetOffset = tabWidth * selectedTabIndex
        val pagerOffset = tabWidth * pagerState.currentPageOffsetFraction
        val indicatorOffset = targetOffset + pagerOffset
        val animatedIndicatorOffset by animateDpAsState(
            targetValue = indicatorOffset,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            label = "indicator_offset"
        )
        Box(
            modifier = Modifier
                .offset(x = animatedIndicatorOffset)
                .width(tabWidth)
                .height(48.dp)
                .padding(4.dp)
                .background(color = indicatorColor, shape = RoundedCornerShape(20.dp))
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEachIndexed { index, title ->
                AnimatedTab(
                    title = title,
                    selected = selectedTabIndex == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AnimatedTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "tab_color"
    )
    val animatedFontWeight by remember { derivedStateOf { if (selected) FontWeight.Bold else FontWeight.Medium } }
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp)),
        onClick = onClick,
        color = Color.Transparent,
        contentColor = animatedColor,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = title, color = animatedColor, style = MaterialTheme.typography.titleSmall, fontWeight = animatedFontWeight)
        }
    }
}

@Composable
private fun PlaylistsTab(onPlaylistClicked: (com.example.holodex.data.db.PlaylistEntity) -> Unit) {
    PlaylistsScreen(
        modifier = Modifier.fillMaxSize(),
        playlistManagementViewModel = hiltViewModel(),
        onPlaylistClicked = onPlaylistClicked
    )
}

@Composable
private fun FavoritesTab(
    isGridView: Boolean,
    navController: NavController,
    playlistManagementViewModel: PlaylistManagementViewModel
) {
    FavoritesScreen(
        isGridView = isGridView,
        modifier = Modifier.fillMaxSize(),
        videoListViewModel = hiltViewModel(),
        favoritesViewModel = hiltViewModel(),
        playlistManagementViewModel = playlistManagementViewModel,
        navController = navController
    )
}

@Composable
private fun HistoryTab(
    navController: NavController,
    playlistManagementViewModel: PlaylistManagementViewModel
) {
    HistoryScreen(
        modifier = Modifier.fillMaxSize(),
        navController = navController,
        videoListViewModel = hiltViewModel(),
        favoritesViewModel = hiltViewModel(),
        playlistManagementViewModel = playlistManagementViewModel
    )
}