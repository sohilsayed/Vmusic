package com.example.holodex.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.util.PlaylistFormatter

@Composable
fun PlaylistCard(
    playlist: PlaylistStub,
    onPlaylistClicked: (PlaylistStub) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // --- START OF IMPLEMENTATION ---
    val (type, displayTitle, displayDescription) = remember(playlist) {
        val playlistType = if (playlist.id.startsWith(":")) playlist.id.substringBefore('[') else "ugp"
        val title = PlaylistFormatter.getDisplayTitle(playlist, context) { en, jp -> jp?.takeIf { it.isNotBlank() } ?: en }
        val description = PlaylistFormatter.getDisplayDescription(playlist, context) { en, jp -> jp?.takeIf { it.isNotBlank() } ?: en }
        Triple(playlistType, title, description)
    }

    val textToShow = when (type) {
        ":artist", ":hot" -> displayDescription ?: displayTitle
        ":dailyrandom", ":weekly", ":mv", ":latest" -> displayDescription ?: displayTitle
        else -> displayTitle // For UGP and others, show the title
    }
    // --- END OF IMPLEMENTATION ---

    Card(
        modifier = modifier
            .width(140.dp)
            .clickable { onPlaylistClicked(playlist) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            PlaylistArtwork(
                playlist = playlist,
                modifier = Modifier
            )
            Text(
                text = textToShow,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}