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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.holodex.domain.action.GlobalMediaActionHandler
import com.example.holodex.ui.dialogs.AddExternalChannelDialog
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.viewmodel.AddChannelViewModel
import com.example.holodex.viewmodel.LibraryType
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
    contentPadding: PaddingValues,
    actionHandler: GlobalMediaActionHandler // Consolidated Handler
) {
    // Default to 'Favorites' (index 1)
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { LibraryTab.entries.size })
    val coroutineScope = rememberCoroutineScope()

    val addChannelViewModel: AddChannelViewModel = hiltViewModel()
    val showAddChannelDialog by addChannelViewModel.showDialog.collectAsStateWithLifecycle()

    if (showAddChannelDialog) {
        AddExternalChannelDialog(onDismissRequest = { addChannelViewModel.closeDialog() })
    }

    Scaffold(
        topBar = {
            LibraryTopAppBar()
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addChannelViewModel.openDialog() },
                // Adjust FAB padding to sit above the bottom navigation bar
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding())
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add External Channel")
            }
        }
    ) { innerPadding ->

        // Combine TopAppBar padding with BottomNavBar padding
        val unifiedPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = unifiedPadding.calculateTopPadding())
        ) {
            // Animated Tabs
            AnimatedCustomTabRow(
                selectedTabIndex = pagerState.currentPage,
                tabs = LibraryTab.entries.map { stringResource(it.titleRes) },
                pagerState = pagerState,
                onTabSelected = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                }
            )

            // Pager Content
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                // Calculate specific padding for lists inside the pager
                val listPadding = PaddingValues(bottom = unifiedPadding.calculateBottomPadding())

                when (LibraryTab.entries[page]) {
                    LibraryTab.FAVORITES -> {
                        FavoritesScreen(
                            actions = actionHandler,
                            contentPadding = listPadding,
                            isGridView = false
                        )
                    }

                    LibraryTab.PLAYLISTS -> {
                        PlaylistsTab(
                            onPlaylistClicked = { playlist ->
                                val idToNavigate = when {
                                    playlist.playlistId < 0 && playlist.serverId == null -> playlist.playlistId.toString()
                                    playlist.playlistId > 0 -> playlist.playlistId.toString()
                                    else -> playlist.serverId
                                }
                                if (!idToNavigate.isNullOrBlank()) {
                                    navController.navigate(AppDestinations.playlistDetailsRoute(idToNavigate))
                                }
                            },
                            contentPadding = listPadding
                        )
                    }

                    LibraryTab.HISTORY -> {
                        // Replaces old HistoryScreen
                        StandardMediaListScreen(
                            libraryType = LibraryType.HISTORY,
                            actions = actionHandler,
                            contentPadding = listPadding
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopAppBar() {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.bottom_nav_library),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
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
        // Background Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                )
        )
        // Sliding Indicator
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
        // Text Items
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
            Text(
                text = title,
                color = animatedColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = animatedFontWeight
            )
        }
    }
}

@Composable
private fun PlaylistsTab(
    onPlaylistClicked: (com.example.holodex.data.db.PlaylistEntity) -> Unit,
    contentPadding: PaddingValues
) {
    // Reuses existing PlaylistsScreen logic
    PlaylistsScreen(
        modifier = Modifier.fillMaxSize(),
        playlistManagementViewModel = hiltViewModel(),
        onPlaylistClicked = onPlaylistClicked,
        contentPadding = contentPadding
    )
}