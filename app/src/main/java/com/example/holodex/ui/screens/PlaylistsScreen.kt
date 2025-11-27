package com.example.holodex.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.holodex.R
import com.example.holodex.data.db.PlaylistEntity
import com.example.holodex.ui.dialogs.CreatePlaylistDialog
import com.example.holodex.viewmodel.PlaylistManagementViewModel

@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    playlistManagementViewModel: PlaylistManagementViewModel,
    onPlaylistClicked: (PlaylistEntity) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp) // NEW PARAMETER
) {
    val playlists: List<PlaylistEntity> by playlistManagementViewModel.allDisplayablePlaylists.collectAsStateWithLifecycle()
    val showCreateDialog by playlistManagementViewModel.showCreatePlaylistDialog.collectAsStateWithLifecycle()

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismissRequest = { playlistManagementViewModel.closeCreatePlaylistDialog() },
            onCreatePlaylist = { name, description ->
                playlistManagementViewModel.confirmCreatePlaylist(name, description)
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.message_no_playlists_yet))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { playlistManagementViewModel.openCreatePlaylistDialog() }) {
                        Text(stringResource(R.string.action_create_playlist))
                    }
                }
            }
        } else {
            LazyColumn(
                // *** FIX: Use the dynamic content padding ***
                contentPadding = contentPadding
            ) {
                items(playlists, key = { it.playlistId }) { playlist ->
                    PlaylistItemRow(
                        playlist = playlist,
                        onPlaylistClicked = { onPlaylistClicked(playlist) },
                        onDeleteClicked = { playlistManagementViewModel.deletePlaylist(playlist) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistItemRow(
    playlist: PlaylistEntity,
    onPlaylistClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    ListItem(
        headlineContent = { Text(playlist.name ?: "Untitled Playlist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            playlist.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = "Playlist icon",
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            IconButton(onClick = onDeleteClicked) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete_playlist))
            }
        },
        modifier = Modifier.clickable(onClick = onPlaylistClicked)
    )
}