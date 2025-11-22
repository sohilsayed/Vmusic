package com.example.holodex.ui.composables

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.holodex.R

@Composable
fun ItemOptionsMenu(
    state: ItemMenuState,
    actions: ItemMenuActions,
    expanded: Boolean,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val onShare = { textToShare: String ->
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, textToShare)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
        onDismissRequest()
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_add_to_queue)) },
            onClick = {
                actions.onAddToQueue()
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }
        )

        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_add_to_playlist_menu)) },
            onClick = {
                actions.onAddToPlaylist()
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }
        )

        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_share)) },
            onClick = { onShare(state.shareUrl) },
            leadingIcon = { Icon(Icons.Filled.Share, null) }
        )

        if (state.canBeDownloaded) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_download)) },
                onClick = {
                    actions.onDownload()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Download, null) }
            )
        }

        if (state.isDownloaded) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    actions.onDelete()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Delete, null) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (state.isSegment) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_view_video)) },
                onClick = {
                    actions.onGoToVideo(state.videoId)
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Movie, null) }
            )
        }

        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_view_artist)) },
            onClick = {
                actions.onGoToArtist(state.channelId)
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.Filled.Person, null) },
            enabled = state.channelId.isNotBlank()
        )
    }
}
/**
 * A state holder for the ItemOptionsMenu. It contains all the necessary
 * data to determine the visibility and enabled status of menu items.
 */
@Immutable
data class ItemMenuState(
    val isDownloaded: Boolean,
    val isSegment: Boolean,
    val canBeDownloaded: Boolean,
    val shareUrl: String,
    val videoId: String,
    val channelId: String
)

/**
 * A holder for all the possible actions a user can take from the ItemOptionsMenu.
 * The parent composable is responsible for providing the implementations for these actions.
 */
@Immutable
data class ItemMenuActions(
    val onAddToQueue: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onShare: (String) -> Unit,
    val onDownload: () -> Unit,
    val onDelete: () -> Unit,
    val onGoToVideo: (String) -> Unit,
    val onGoToArtist: (String) -> Unit,
)