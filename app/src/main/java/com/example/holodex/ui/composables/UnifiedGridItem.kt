package com.example.holodex.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.viewmodel.UnifiedDisplayItem

/**
 * A universal, reusable composable for displaying any music-related item in a grid or carousel.
 * It intelligently displays badges and context based on the properties of the UnifiedDisplayItem.
 *
 * @param item The canonical UnifiedDisplayItem containing all necessary data for display.
 * @param onClick The action to perform when the card is clicked.
 * @param modifier The modifier to be applied to the Card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedGridItem(
    item: UnifiedDisplayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentUrlIndex by remember(item.artworkUrls) { mutableIntStateOf(0) }

    Card(
        onClick = onClick,
        modifier = modifier.width(140.dp)
    ) {
        Column {
            Box(contentAlignment = Alignment.BottomStart) {
                // Main Artwork Image
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.artworkUrls.getOrNull(currentUrlIndex))
                        .placeholder(R.drawable.ic_placeholder_image)
                        .error(R.drawable.ic_error_image)
                        .crossfade(true)
                        .build(),
                    onError = {
                        // If the primary URL fails, try the next one in the prioritized list
                        if (currentUrlIndex < item.artworkUrls.lastIndex) {
                            currentUrlIndex++
                        }
                    },
                    contentDescription = stringResource(R.string.content_desc_album_art_for, item.title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                )

                // Gradient scrim for text readability
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 100f // Start gradient lower down
                            )
                        )
                )

                // Badges and Duration, overlaid on the artwork
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isDownloaded) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = stringResource(R.string.content_description_download_status, stringResource(R.string.status_completed)),
                            tint = Color.White,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = item.durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Text content below the artwork
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artistText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}