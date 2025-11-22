// File: java/com/example/holodex/ui/screens/DiscoveryScreen.kt

package com.example.holodex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.R
import com.example.holodex.auth.AuthViewModel
import com.example.holodex.data.model.discovery.DiscoveryChannel
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.data.model.discovery.SingingStreamShelfItem
import com.example.holodex.ui.composables.CarouselShelf
import com.example.holodex.ui.composables.ChannelCard
import com.example.holodex.ui.composables.HeroCarousel
import com.example.holodex.ui.composables.PlaylistCard
import com.example.holodex.ui.composables.UnifiedGridItem
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.util.findActivity
import com.example.holodex.viewmodel.DiscoveryViewModel
import com.example.holodex.viewmodel.MusicCategoryType
import com.example.holodex.viewmodel.ShelfType
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.VideoListViewModel
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.state.UiState
import kotlinx.coroutines.flow.collectLatest
import org.orbitmvi.orbit.compose.collectAsState

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    navController: NavController,
) {
    val discoveryViewModel: DiscoveryViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val videoListViewModel: VideoListViewModel = hiltViewModel(findActivity())

    val uiState by discoveryViewModel.uiState.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val videoListState by videoListViewModel.collectAsState()
    val selectedOrg = videoListState.selectedOrganization
    val availableOrganizations = videoListState.availableOrganizations
    val context = LocalContext.current

    var showOrgMenu by remember { mutableStateOf(false) }

    LaunchedEffect(authState, selectedOrg) {
        discoveryViewModel.loadDiscoveryContent(selectedOrg, authState)
    }

    LaunchedEffect(Unit) {
        discoveryViewModel.transientMessage.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bottom_nav_discover)) },
                actions = {
                    Box {
                        TextButton(onClick = { showOrgMenu = true }) {
                            Text(selectedOrg)
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Select Organization"
                            )
                        }
                        DropdownMenu(
                            expanded = showOrgMenu,
                            onDismissRequest = { showOrgMenu = false }
                        ) {
                            // --- START OF MODIFICATION ---
                            // Use the dynamic list from the ViewModel
                            availableOrganizations.forEach { (name, value) ->
                                if (value != null) {
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            videoListViewModel.setOrganization(value)
                                            showOrgMenu = false
                                        }
                                    )
                                }
                            }
                            // --- END OF MODIFICATION ---
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(uiState.shelfOrder, key = { it.name }) { shelfType ->
                val shelfState = uiState.shelves[shelfType] ?: UiState.Loading

                when (shelfType) {
                    ShelfType.RECENT_STREAMS -> {
                        // Use the new HeroCarousel for this specific shelf
                        @Suppress("UNCHECKED_CAST")
                        HeroCarousel(
                            title = shelfType.toTitle(selectedOrg),
                            uiState = shelfState as UiState<List<SingingStreamShelfItem>>,
                            onItemClicked = { item ->
                                navController.navigate(AppDestinations.videoDetailRoute(item.video.id))
                            }
                        )
                    }

                    else -> {
                        // Use the standard CarouselShelf for all other shelves
                        CarouselShelf<Any>(
                            title = shelfType.toTitle(selectedOrg),
                            uiState = shelfState,
                            itemContent = { item ->
                                ShelfItemContent(
                                    item = item,
                                    discoveryViewModel = discoveryViewModel,
                                    navController = navController
                                )
                            },
                            actionContent = {
                                if (shelfType == ShelfType.TRENDING_SONGS) {
                                    TextButton(onClick = {
                                        (shelfState as? UiState.Success)?.data?.let {
                                            discoveryViewModel.addAllToQueue(it.filterIsInstance<UnifiedDisplayItem>())
                                        }
                                    }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.QueueMusic,
                                            null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize)
                                        )
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text("Queue")
                                    }
                                } else {
                                    TextButton(onClick = {
                                        navController.navigate(
                                            AppDestinations.fullListViewRoute(
                                                shelfType.toMusicCategoryType(),
                                                selectedOrg
                                            )
                                        )
                                    }) {
                                        Text(stringResource(R.string.action_show_more))
                                    }
                                }
                            }
                        )
                    }
                }
                // --- END OF MODIFICATION ---
            }
        }
    }
}

    @Composable
private fun ShelfItemContent(
    item: Any,
    discoveryViewModel: DiscoveryViewModel,
    navController: NavController
) {
    when (item) {
        is UnifiedDisplayItem -> UnifiedGridItem(item = item, onClick = { discoveryViewModel.playUnifiedItem(item) })
        is SingingStreamShelfItem -> {
            val displayShell = item.video.toUnifiedDisplayItem(isLiked = false, downloadedSegmentIds = emptySet())
            UnifiedGridItem(
                item = displayShell,
                onClick = {
                    navController.navigate(AppDestinations.videoDetailRoute(item.video.id))
                }
            )
        }
        is PlaylistStub -> PlaylistCard(
            playlist = item,
            onPlaylistClicked = { playlistStub ->
                // Decide what to do based on the type
                if (playlistStub.type.startsWith("radio")) {
                    discoveryViewModel.playRadioPlaylist(playlistStub)
                } else {
                    navController.navigate(AppDestinations.playlistDetailsRoute(playlistStub.id))
                }
            }
        )
        is DiscoveryChannel -> ChannelCard(
            channel = item,
            onChannelClicked = { channelId -> navController.navigate("channel_details/$channelId") }
        )
    }
}


private fun ShelfType.toTitle(orgName: String): String {
    val displayOrg = if (orgName == "All Vtubers") "All" else orgName
    return when (this) {
        ShelfType.RECENT_STREAMS -> "Recent Singing Streams"
        ShelfType.SYSTEM_PLAYLISTS -> "$displayOrg Playlists"
        ShelfType.ARTIST_RADIOS -> "$displayOrg Radios"
        ShelfType.FAN_PLAYLISTS -> "$displayOrg Community Playlists"
        ShelfType.TRENDING_SONGS -> "Trending Songs"
        ShelfType.DISCOVER_CHANNELS -> "Discover $displayOrg"
        ShelfType.FOR_YOU -> "For You"
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun ShelfType.toMusicCategoryType(): MusicCategoryType {
    return when (this) {
        ShelfType.TRENDING_SONGS -> MusicCategoryType.TRENDING
        ShelfType.RECENT_STREAMS -> MusicCategoryType.RECENT_STREAMS
        ShelfType.FAN_PLAYLISTS -> MusicCategoryType.COMMUNITY_PLAYLISTS
        ShelfType.ARTIST_RADIOS -> MusicCategoryType.ARTIST_RADIOS
        // --- START OF MODIFICATION ---
        ShelfType.SYSTEM_PLAYLISTS -> MusicCategoryType.SYSTEM_PLAYLISTS
        ShelfType.DISCOVER_CHANNELS -> MusicCategoryType.DISCOVER_CHANNELS
        // --- END OF MODIFICATION ---
        ShelfType.FOR_YOU -> MusicCategoryType.FAVORITES // For You is a special case of favorites feed
    }
}