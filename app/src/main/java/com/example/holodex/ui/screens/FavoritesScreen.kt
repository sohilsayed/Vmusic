package com.example.holodex.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.domain.action.GlobalMediaActionHandler
import com.example.holodex.ui.composables.LoadingSkeleton
import com.example.holodex.ui.composables.UnifiedListItem
import com.example.holodex.viewmodel.FavoritesSideEffect
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.UnifiedDisplayItem
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(UnstableApi::class)
@Composable
fun FavoritesScreen(
    actions: GlobalMediaActionHandler, // Injected Handler
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    isGridView: Boolean = false,
    favoritesViewModel: FavoritesViewModel = hiltViewModel()
) {
    val state by favoritesViewModel.collectAsState()
    val context = LocalContext.current

    favoritesViewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is FavoritesSideEffect.ShowToast -> {
                Toast.makeText(context, sideEffect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    var isChannelsExpanded by remember { mutableStateOf(true) }
    var isFavoritesExpanded by remember { mutableStateOf(true) }
    var isSegmentsExpanded by remember { mutableStateOf(true) }

    if (state.isLoading) {
        LoadingSkeleton(itemCount = 8, modifier = modifier.padding(16.dp))
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        // --- SECTION 1: CHANNELS ---
        if (state.favoriteChannels.isNotEmpty()) {
            item {
                ExpandableSectionHeader(
                    title = stringResource(R.string.category_favorite_channels),
                    itemCount = state.favoriteChannels.size,
                    isExpanded = isChannelsExpanded,
                    onToggle = { isChannelsExpanded = !isChannelsExpanded }
                )
            }
            if (isChannelsExpanded) {
                item {
                    FavoriteChannelsRow(
                        channels = state.favoriteChannels,
                        onChannelClick = { channelItem ->
                            actions.onNavigateToChannel(channelItem.channelId)
                        }
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        }

        // --- SECTION 2: FAVORITE VIDEOS ---
        item {
            ExpandableSectionHeader(
                title = stringResource(R.string.category_favorites),
                itemCount = state.unifiedFavoritedVideos.size,
                isExpanded = isFavoritesExpanded,
                onToggle = { isFavoritesExpanded = !isFavoritesExpanded }
            )
        }

        if (isFavoritesExpanded && state.unifiedFavoritedVideos.isNotEmpty()) {
            if (isGridView) {
                item {
                    FavoritesGrid(
                        items = state.unifiedFavoritedVideos,
                        onItemClicked = { item ->
                            actions.onNavigateToVideo(item.videoId)
                        }
                    )
                }
            } else {
                itemsIndexed(
                    items = state.unifiedFavoritedVideos,
                    key = { _, it -> it.stableId }
                ) { index, item ->
                    UnifiedListItem(
                        item = item,
                        actions = actions,
                        onItemClick = { actions.onNavigateToVideo(item.videoId) }
                    )
                }
            }
        }

        // --- SECTION 3: LIKED SEGMENTS (SONGS) ---
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ExpandableSectionHeader(
                title = stringResource(R.string.category_liked_segments),
                itemCount = state.unifiedLikedSegments.size,
                isExpanded = isSegmentsExpanded,
                onToggle = { isSegmentsExpanded = !isSegmentsExpanded }
            )
        }

        if (isSegmentsExpanded && state.unifiedLikedSegments.isNotEmpty()) {
            if (isGridView) {
                item {
                    FavoritesGrid(
                        items = state.unifiedLikedSegments,
                        onItemClicked = { item ->
                            val index = state.unifiedLikedSegments.indexOf(item)
                            if (index != -1) actions.onPlay(state.unifiedLikedSegments, index)
                        }
                    )
                }
            } else {
                itemsIndexed(
                    items = state.unifiedLikedSegments,
                    key = { _, it -> it.stableId }
                ) { index, item ->
                    UnifiedListItem(
                        item = item,
                        actions = actions,
                        onItemClick = { actions.onPlay(state.unifiedLikedSegments, index) }
                    )
                }
            }
        }
    }
}

// --- Local Helpers ---

@Composable
private fun FavoritesGrid(
    items: List<UnifiedDisplayItem>,
    onItemClicked: (UnifiedDisplayItem) -> Unit,
    ) {

    val rows = (items.size + 1) / 2
    val height = (rows * 180).dp // Approx height

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.height(height),
        userScrollEnabled = false // Scroll handled by parent LazyColumn
    ) {
        items(items, key = { it.stableId }) { item ->
            // Reusing the grid item logic locally or from Composables
            Card(onClick = { onItemClicked(item) }) {
                Column {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.artworkUrls.firstOrNull())
                            .crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableSectionHeader(
    title: String,
    itemCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = "expansion_arrow"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ($itemCount)",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.rotate(rotationAngle)
        )
    }
}


@Composable
private fun FavoriteChannelsRow(
    channels: List<UnifiedDisplayItem>,
    onChannelClick: (UnifiedDisplayItem) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(channels, key = { it.stableId }) { channelItem ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(80.dp)
                    .clip(MaterialTheme.shapes.small) // Clip for ripple effect
                    .clickable {

                        onChannelClick(channelItem)
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channelItem.artworkUrls.firstOrNull())
                        .placeholder(R.drawable.ic_placeholder_image)
                        .crossfade(true).build(),
                    contentDescription = channelItem.title,
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = channelItem.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}