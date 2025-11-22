package com.example.holodex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.R
import com.example.holodex.ui.composables.EmptyState
import com.example.holodex.ui.composables.UnifiedListItem
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.HistoryViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.VideoListViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    videoListViewModel: VideoListViewModel,
    favoritesViewModel: FavoritesViewModel,
    playlistManagementViewModel: PlaylistManagementViewModel
) {
    val historyViewModel: HistoryViewModel = hiltViewModel()
    val historyItems by historyViewModel.unifiedHistoryItems.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        historyViewModel.transientMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (historyItems.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.message_no_history),
                onRefresh = {}
            )
        } else {
            HistoryHeader(
                songCount = historyItems.size,
                onPlayAll = { historyViewModel.playAllHistory() },
                onAddAllToQueue = { historyViewModel.addAllHistoryToQueue() }
            )
            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(
                    items = historyItems,
                    key = { item -> item.stableId } // FIX: Corrected typo from 'stableld'
                ) { item ->
                    UnifiedListItem(
                        item = item,
                        onItemClicked = { historyViewModel.playFromHistoryItem(item) },
                        videoListViewModel = videoListViewModel,
                        favoritesViewModel = favoritesViewModel,
                        playlistManagementViewModel = playlistManagementViewModel,
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(
    songCount: Int,
    onPlayAll: () -> Unit,
    onAddAllToQueue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = stringResource(id = R.string.recently_played_songs),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = pluralStringResource(
                    id = R.plurals.song_count_label,
                    count = songCount, // FIX: Pass count parameter
                    songCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(id = R.string.action_play))
            }
            OutlinedButton(
                onClick = onAddAllToQueue,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(id = R.string.action_add_to_queue))
            }
        }
    }
}