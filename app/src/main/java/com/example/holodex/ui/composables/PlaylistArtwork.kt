package com.example.holodex.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.data.model.discovery.PlaylistStub
import com.example.holodex.util.ArtworkResolver
import com.example.holodex.util.PlaylistFormatter
import java.util.Locale
import kotlin.math.max

@Composable
fun PlaylistArtwork(
    playlist: PlaylistStub,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val artworkUrl = remember(playlist.id) {
        ArtworkResolver.getPlaylistArtworkUrl(playlist)
    }
    val (type, title) = remember(playlist.id, playlist.title) {
        val formattedTitle = PlaylistFormatter.getDisplayTitle(playlist, context) { englishName, japaneseName ->
            japaneseName?.takeIf { it.isNotBlank() } ?: englishName
        }
        val playlistType = if (playlist.id.startsWith(":")) {
            playlist.id.substringBefore('[')
        } else {
            "ugp"
        }
        playlistType to formattedTitle
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium),
        shadowElevation = 2.dp
    ) {
        when (type) {
            ":artist", ":hot" -> RadioTextArt(
                titleText = title,
                imageUrl = artworkUrl,
            )
            ":dailyrandom", ":weekly", ":mv", ":latest" -> {
                val lastSpaceIndex = title.lastIndexOf(' ')
                val (typeText, titleText) = if (lastSpaceIndex > 0 && title.length > lastSpaceIndex + 1) {
                    title.substring(0, lastSpaceIndex) to title.substring(lastSpaceIndex + 1)
                } else {
                    title.substringBefore(": ") to title.substringAfter(": ", title)
                }
                StackedTextArt(
                    typeText = typeText,
                    titleText = titleText,
                    imageUrl = artworkUrl
                )
            }
            else -> OverlayTextArt(
                titleText = title,
                imageUrl = artworkUrl
            )
        }
    }
}

@Composable
private fun OverlayTextArt(
    titleText: String,
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .placeholder(R.drawable.ic_placeholder_image)
                .error(R.drawable.ic_error_image)
                .crossfade(true)
                .build(),
            contentDescription = titleText,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 150f
                    )
                )
        )
        Text(
            text = titleText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun RadioTextArt(
    titleText: String,
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                    ),
                    radius = 250f
                )
            )
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Podcasts,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.9f)
                .align(Alignment.Center),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .placeholder(R.drawable.ic_placeholder_image)
                    .error(R.drawable.ic_error_image)
                    .crossfade(true)
                    .build(),
                contentDescription = titleText,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .aspectRatio(1f)
                    .shadow(elevation = 8.dp, shape = CircleShape)
                    .clip(CircleShape)
            )
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = stringResource(id = R.string.sgp_radio_type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StackedTextArt(
    typeText: String,
    titleText: String,
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    val adjFontSize = max(14, (18 - (titleText.length / 15))).sp

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = typeText.toUpperCase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = adjFontSize),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .placeholder(R.drawable.ic_placeholder_image)
                .error(R.drawable.ic_error_image)
                .crossfade(true)
                .build(),
            contentDescription = titleText,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
        )
    }
}