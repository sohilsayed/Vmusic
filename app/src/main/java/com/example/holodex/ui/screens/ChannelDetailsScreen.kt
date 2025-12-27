package com.example.holodex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.model.discovery.DiscoveryChannel
import com.example.holodex.domain.action.GlobalMediaActionHandler
import com.example.holodex.ui.composables.CarouselShelf
import com.example.holodex.ui.composables.ChannelCard
import com.example.holodex.ui.composables.ErrorStateWithRetry
import com.example.holodex.ui.composables.LoadingState
import com.example.holodex.ui.composables.SimpleProcessedBackground
import com.example.holodex.ui.composables.UnifiedGridItem
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.util.ThumbnailQuality
import com.example.holodex.util.getYouTubeThumbnailUrl
import com.example.holodex.viewmodel.ChannelDetailsSideEffect
import com.example.holodex.viewmodel.ChannelDetailsViewModel
import com.example.holodex.viewmodel.FavoritesViewModel
import com.example.holodex.viewmodel.MusicCategoryType
import com.example.holodex.viewmodel.UnifiedDisplayItem
import com.example.holodex.viewmodel.state.UiState
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailsScreen(
    navController: NavController,
    onNavigateUp: () -> Unit,
    // FIX: Removed "= hiltViewModel()". It must be passed explicitly.
    actionHandler: GlobalMediaActionHandler
) {
    val channelViewModel: ChannelDetailsViewModel = hiltViewModel()
    val favoritesViewModel: FavoritesViewModel = hiltViewModel()

    val state by channelViewModel.collectAsState()
    val favoritesState by favoritesViewModel.collectAsState()
    val context = LocalContext.current

    // Handle Side Effects (Toasts)
    channelViewModel.collectSideEffect { effect ->
        when(effect) {
            is ChannelDetailsSideEffect.ShowToast -> {
                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Determine Background Image
    val backgroundImageUrl by remember(state) {
        derivedStateOf {
            state.channelDetails?.bannerUrl?.takeIf { it.isNotBlank() }
                ?: state.latestVideos.firstOrNull()?.videoId?.let {
                    getYouTubeThumbnailUrl(it, ThumbnailQuality.MAX).firstOrNull()
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Background
        SimpleProcessedBackground(
            artworkUri = backgroundImageUrl,
            dynamicColor = state.dynamicTheme.primary
        )

        // 2. Foreground Content
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = state.dynamicTheme.onPrimary
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Box(modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()) {

                if (state.isLoading) {
                    LoadingState(message = stringResource(R.string.loading))
                } else if (state.error != null) {
                    ErrorStateWithRetry(
                        message = state.error!!,
                        onRetry = { /* Implement specific retry intent if needed */ }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // A. Channel Header
                        item {
                            state.channelDetails?.let { details ->
                                ChannelHeader(
                                    details = details,
                                    isFavorited = favoritesState.likedItemsMap.containsKey(details.id),
                                    onFavoriteClicked = {
                                        favoritesViewModel.toggleFavoriteChannel(details)
                                    },
                                    textColor = state.dynamicTheme.onPrimary
                                )
                            }
                        }

                        // B. Popular Songs (Hot)
                        if (state.popularSongs.isNotEmpty()) {
                            item {
                                CarouselShelf<UnifiedDisplayItem>(
                                    title = "Popular",
                                    uiState = UiState.Success(state.popularSongs),
                                    actionContent = {
                                        TextButton(onClick = {
                                            navController.navigate(
                                                AppDestinations.fullListViewRoute(
                                                    MusicCategoryType.TRENDING,
                                                    channelViewModel.channelId
                                                )
                                            )
                                        }) {
                                            Text(stringResource(id = R.string.action_show_more))
                                        }
                                    },
                                    itemContent = { item ->
                                        UnifiedGridItem(
                                            item = item,
                                            onClick = { actionHandler.onPlay(item) }
                                        )
                                    }
                                )
                            }
                        }

                        // C. Latest Videos / Uploads
                        if (state.latestVideos.isNotEmpty()) {
                            item {
                                CarouselShelf<UnifiedDisplayItem>(
                                    title = if (state.isExternal) "Uploads" else "Latest Streams",
                                    uiState = UiState.Success(state.latestVideos),
                                    actionContent = {
                                        TextButton(onClick = {
                                            actionHandler.onNavigateToChannel(channelViewModel.channelId)
                                        }) {
                                            Text(stringResource(id = R.string.action_show_more))
                                        }
                                    },
                                    itemContent = { item ->
                                        UnifiedGridItem(
                                            item = item,
                                            onClick = {
                                                actionHandler.onNavigateToVideo(item.videoId)
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        // D. Other Channels
                        val otherChannels = state.discoveryContent?.channels ?: emptyList()
                        if (otherChannels.isNotEmpty()) {
                            item {
                                val orgName = state.channelDetails?.org ?: "Organization"
                                CarouselShelf<DiscoveryChannel>(
                                    title = "Discover More from $orgName",
                                    uiState = UiState.Success(otherChannels),
                                    actionContent = {},
                                    itemContent = { channel ->
                                        ChannelCard(
                                            channel = channel,
                                            onChannelClicked = { id ->
                                                actionHandler.onNavigateToChannel(id)
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    details: ChannelDetails,
    isFavorited: Boolean,
    onFavoriteClicked: () -> Unit,
    textColor: Color
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(details.photoUrl)
                .crossfade(true).build(),
            contentDescription = "Channel Avatar",
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = details.englishName ?: details.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
            details.org?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onFavoriteClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = textColor,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (isFavorited) "Favorited" else "Favorite")
            }

            OutlinedButton(
                onClick = { uriHandler.openUri("https://youtube.com/channel/${details.id}") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.5f))
            ) {
                Icon(painterResource(R.drawable.youtube), contentDescription = "YouTube")
            }

            details.twitter?.let {
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://twitter.com/${it}") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
                    border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.5f))
                ) {
                    Icon(painterResource(R.drawable.twitter), contentDescription = "Twitter")
                }
            }
        }
    }
}