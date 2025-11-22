@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.holodex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.R
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.ui.composables.EmptyState
import com.example.holodex.ui.composables.ErrorStateWithRetry
import com.example.holodex.ui.composables.LoadingState
import com.example.holodex.ui.composables.SimpleProcessedBackground
import com.example.holodex.ui.composables.UnifiedListItem
import com.example.holodex.util.ArtworkResolver
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.findActivity
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.PlaylistDetailsSideEffect
import com.example.holodex.viewmodel.PlaylistDetailsViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.VideoListViewModel
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(UnstableApi::class)
@Composable
fun PlaylistDetailsScreen(
    navController: NavController,
    onNavigateUp: () -> Unit,
    playlistManagementViewModel: PlaylistManagementViewModel
) {
    val playlistDetailsViewModel: PlaylistDetailsViewModel = hiltViewModel()

    // --- ORBIT STATE COLLECTION ---
    val state by playlistDetailsViewModel.collectAsState()

    // Derived variables for cleaner usage
    val playlistDetails = state.playlist
    val items = state.items
    val isEditMode = state.isEditMode
    val editablePlaylist = state.editablePlaylist
    val dynamicTheme = state.dynamicTheme
    val isPlaylistOwned = state.isPlaylistOwned
    val isShuffleActive = state.isShuffleActive

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val videoListViewModel: VideoListViewModel = hiltViewModel(findActivity())
    val favoritesViewModel: FavoritesViewModel = hiltViewModel()

    val backgroundImageUrl by remember(items, playlistDetails) {
        derivedStateOf {
            items.firstOrNull()?.artworkUrls?.firstOrNull()
                ?: playlistDetails?.let {
                    val stub = PlaylistStub(it.serverId ?: it.playlistId.toString(), it.name ?: "", "", null, it.description)
                    ArtworkResolver.getPlaylistArtworkUrl(stub)
                }
        }
    }

    // --- ORBIT SIDE EFFECTS ---
    playlistDetailsViewModel.collectSideEffect { effect ->
        when (effect) {
            is PlaylistDetailsSideEffect.ShowToast -> {
                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
                playlistDetailsViewModel.clearError()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (!isEditMode) {
                        Text(
                            text = playlistDetails?.name ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(onClick = { playlistDetailsViewModel.cancelEditMode() }) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel Edit")
                        }
                        IconButton(onClick = { playlistDetailsViewModel.saveChanges() }) {
                            Icon(Icons.Default.Check, contentDescription = "Save Changes")
                        }
                    } else {
                        if (isPlaylistOwned) {
                            IconButton(onClick = { playlistDetailsViewModel.enterEditMode() }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Playlist")
                            }
                        }
                        IconButton(onClick = { playlistDetailsViewModel.togglePlaylistShuffleMode() }) {
                            Icon(
                                painter = painterResource(id = if (isShuffleActive) R.drawable.ic_shuffle_on_24 else R.drawable.ic_shuffle_off_24),
                                contentDescription = stringResource(R.string.action_shuffle),
                                tint = if (isShuffleActive) dynamicTheme.primary else dynamicTheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = dynamicTheme.onPrimary,
                    navigationIconContentColor = dynamicTheme.onPrimary,
                    actionIconContentColor = dynamicTheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            SimpleProcessedBackground(
                artworkUri = backgroundImageUrl,
                dynamicColor = dynamicTheme.primary
            )
            CompositionLocalProvider(LocalContentColor provides dynamicTheme.onPrimary) {
                Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                    when {
                        state.isLoading && items.isEmpty() -> {
                            LoadingState(message = stringResource(R.string.loading_content_message))
                        }
                        state.error != null && items.isEmpty() -> {
                            ErrorStateWithRetry(
                                message = state.error!!,
                                onRetry = { playlistDetailsViewModel.loadPlaylistDetails() }
                            )
                        }
                        items.isEmpty() && !state.isLoading -> {
                            EmptyState(
                                message = stringResource(R.string.message_playlist_is_empty),
                                onRefresh = { playlistDetailsViewModel.loadPlaylistDetails() }
                            )
                        }
                        else -> {
                            PlaylistContent(
                                items = items,
                                playlistDetails = if (isEditMode) editablePlaylist else playlistDetails,
                                isEditMode = isEditMode,
                                navController = navController,
                                videoListViewModel = videoListViewModel,
                                favoritesViewModel = favoritesViewModel,
                                playlistManagementViewModel = playlistManagementViewModel,
                                playlistDetailsViewModel = playlistDetailsViewModel,
                                dynamicTheme = dynamicTheme
                            )
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun PlaylistContent(
    items: List<UnifiedDisplayItem>,
    playlistDetails: PlaylistEntity?,
    isEditMode: Boolean,
    navController: NavController,
    videoListViewModel: VideoListViewModel,
    favoritesViewModel: FavoritesViewModel,
    playlistManagementViewModel: PlaylistManagementViewModel,
    playlistDetailsViewModel: PlaylistDetailsViewModel,
    dynamicTheme: DynamicTheme
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            playlistDetailsViewModel.reorderItemInEditMode(from.index, to.index)
        }
    )

    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        item {
            if (isEditMode && playlistDetails != null) {
                EditablePlaylistHeader(
                    playlist = playlistDetails,
                    onNameChange = { playlistDetailsViewModel.updateDraftName(it) },
                    onDescriptionChange = { playlistDetailsViewModel.updateDraftDescription(it) }
                )
            } else {
                PlaylistHeader(
                    playlist = playlistDetails,
                    itemCount = items.size,
                    onPlayAll = { playlistDetailsViewModel.playAllItemsInPlaylist() },
                    onAddAllToQueue = { playlistDetailsViewModel.addAllToQueue() },
                    dynamicTheme = dynamicTheme
                )
            }
            HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.2f))
        }

        itemsIndexed(
            items = items,
            key = { _, item -> item.stableId }
        ) { index, item ->
            ReorderableItem(state = reorderableState, key = item.stableId, enabled = isEditMode) {
                UnifiedListItem(
                    item = item,
                    onItemClicked = { if (!isEditMode) playlistDetailsViewModel.playFromItem(item) },
                    navController = navController,
                    videoListViewModel = videoListViewModel,
                    favoritesViewModel = favoritesViewModel,
                    playlistManagementViewModel = playlistManagementViewModel,
                    isEditing = isEditMode,
                    onRemoveClicked = { playlistDetailsViewModel.removeItemInEditMode(item) },
                    dragHandleModifier = Modifier.longPressDraggableHandle()
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: PlaylistEntity?,
    itemCount: Int,
    onPlayAll: () -> Unit,
    onAddAllToQueue: () -> Unit,
    dynamicTheme: DynamicTheme
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        playlist?.let {
            Text(
                text = it.name ?: "Untitled Playlist",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            val description = it.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(
            text = pluralStringResource(R.plurals.item_count, itemCount, itemCount),
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (itemCount > 0) {
            ActionButtons(
                onPlayAll = {
                    onPlayAll()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onAddAllToQueue = {
                    onAddAllToQueue()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                dynamicTheme = dynamicTheme
            )
        }
    }
}

@Composable
private fun ActionButtons(
    onPlayAll: () -> Unit,
    onAddAllToQueue: () -> Unit,
    dynamicTheme: DynamicTheme
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPlayAll,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = dynamicTheme.onPrimary,
                contentColor = dynamicTheme.primary
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_play_all))
        }

        OutlinedButton(
            onClick = onAddAllToQueue,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = dynamicTheme.onPrimary),
            border = BorderStroke(1.dp, dynamicTheme.onPrimary.copy(alpha = 0.5f))
        ) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(id = R.string.action_add_to_queue))
        }
    }
}