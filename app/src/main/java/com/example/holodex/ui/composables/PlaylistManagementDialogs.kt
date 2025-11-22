package com.example.holodex.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.holodex.ui.dialogs.CreatePlaylistDialog
import com.example.holodex.ui.dialogs.SelectPlaylistDialog
import com.example.holodex.viewmodel.PlaylistManagementViewModel

@Composable
fun PlaylistManagementDialogs(
    playlistManagementViewModel: PlaylistManagementViewModel
) {
    // Using standard lifecycle collection for these booleans
    val showSelectPlaylistDialog by playlistManagementViewModel.showSelectPlaylistDialog.collectAsStateWithLifecycle()
    val userPlaylistsForDialog by playlistManagementViewModel.userPlaylists.collectAsStateWithLifecycle()
    val showCreatePlaylistDialog by playlistManagementViewModel.showCreatePlaylistDialog.collectAsStateWithLifecycle()

    if (showSelectPlaylistDialog) {
        SelectPlaylistDialog(
            playlists = userPlaylistsForDialog,
            onDismissRequest = { playlistManagementViewModel.cancelAddToPlaylistFlow() },
            onPlaylistSelected = { playlist -> playlistManagementViewModel.addItemToExistingPlaylist(playlist) },
            onCreateNewPlaylistClicked = { playlistManagementViewModel.handleCreateNewPlaylistFromSelectionDialog() }
        )
    }
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismissRequest = { playlistManagementViewModel.cancelAddToPlaylistFlow() },
            onCreatePlaylist = { name, desc -> playlistManagementViewModel.confirmCreatePlaylist(name, desc) }
        )
    }
}