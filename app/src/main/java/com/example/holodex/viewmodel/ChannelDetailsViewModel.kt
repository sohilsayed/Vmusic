package com.example.holodex.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.model.discovery.DiscoveryResponse
import com.example.holodex.data.repository.ChannelRepository
import com.example.holodex.data.repository.DiscoveryRepository
import com.example.holodex.data.repository.FeedRepository
import com.example.holodex.data.repository.UnifiedVideoRepository
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.PaletteExtractor
import com.example.holodex.viewmodel.state.BrowseFilterState
import com.example.holodex.viewmodel.state.ViewTypePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

data class ChannelDetailsState(
    val isExternal: Boolean = false,
    val channelDetails: ChannelDetails? = null,
    val dynamicTheme: DynamicTheme = DynamicTheme.default(Color.Black, Color.White),

    // Data Sources
    val discoveryContent: DiscoveryResponse? = null, // Keep for sub-org channels/recommendations
    val popularSongs: ImmutableList<UnifiedDisplayItem> = persistentListOf(),

    // Unified Feed for Videos (Replaces "externalMusicItems" and "recentStreams")
    // This list will hold videos for BOTH External (NewPipe) and Holodex channels
    val latestVideos: ImmutableList<UnifiedDisplayItem> = persistentListOf(),

    val isLoading: Boolean = true,
    val error: String? = null
)

sealed class ChannelDetailsSideEffect {
    data class ShowToast(val message: String) : ChannelDetailsSideEffect()
}

@HiltViewModel
class ChannelDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelRepository: ChannelRepository,
    private val feedRepository: FeedRepository,
    private val discoveryRepository: DiscoveryRepository,
    private val unifiedRepository: UnifiedVideoRepository,
    private val paletteExtractor: PaletteExtractor
) : ContainerHost<ChannelDetailsState, ChannelDetailsSideEffect>, ViewModel() {

    companion object {
        const val CHANNEL_ID_ARG = "channelId"
    }

    val channelId: String = savedStateHandle.get<String>(CHANNEL_ID_ARG) ?: ""

    override val container = container<ChannelDetailsState, ChannelDetailsSideEffect>(ChannelDetailsState()) {
        if (channelId.isNotBlank()) {
            initializeChannel()
        } else {
            intent { reduce { state.copy(isLoading = false, error = "Invalid Channel ID") } }
        }
    }

    private fun initializeChannel() = intent {
        reduce { state.copy(isLoading = true, error = null) }

        // 1. Check Local DB to see if this is a known External channel
        val localChannel = unifiedRepository.getChannel(channelId)
        val isExternal = localChannel?.org == "External"

        reduce { state.copy(isExternal = isExternal) }

        if (isExternal) {
            loadExternalChannel()
        } else {
            loadHolodexChannel()
        }
    }

    private fun loadExternalChannel() = intent {
        // A. Channel Info (Store5)
        channelRepository.getChannel(channelId, isExternal = true)
            .onEach { response ->
                if (response is StoreReadResponse.Data) {
                    val item = response.value
                    // Map UnifiedDisplayItem back to ChannelDetails for UI
                    val details = ChannelDetails(
                        id = item.channelId,
                        name = item.title,
                        englishName = item.title,
                        description = null,
                        photoUrl = item.artworkUrls.firstOrNull(),
                        bannerUrl = null,
                        org = "External",
                        suborg = null,
                        twitter = null,
                        group = null
                    )
                    val theme = paletteExtractor.extractThemeFromUrl(details.photoUrl, DynamicTheme.default(Color.Black, Color.White))
                    reduce { state.copy(channelDetails = details, dynamicTheme = theme, isLoading = false) }
                }
            }.launchIn(viewModelScope)

        // B. External Videos (Direct Fetch via Repository)
        // We map these to the 'latestVideos' list so the UI can use one list for both types
        val videos = channelRepository.getExternalChannelVideos(channelId)
        reduce { state.copy(latestVideos = videos.toImmutableList()) }
    }

    private fun loadHolodexChannel() = intent {
        // A. Channel Info
        channelRepository.getChannel(channelId, isExternal = false)
            .onEach { response ->
                if (response is StoreReadResponse.Data) {
                    val item = response.value
                    val details = ChannelDetails(
                        id = item.channelId,
                        name = item.title,
                        englishName = item.artistText,
                        description = null,
                        photoUrl = item.artworkUrls.firstOrNull(),
                        bannerUrl = null,
                        org = null,
                        suborg = null,
                        twitter = null,
                        group = null
                    )
                    val theme = paletteExtractor.extractThemeFromUrl(details.photoUrl, DynamicTheme.default(Color.Black, Color.White))
                    reduce { state.copy(channelDetails = details, dynamicTheme = theme, isLoading = false) }
                } else if (response is StoreReadResponse.Error) {
                    reduce { state.copy(isLoading = false, error = response.errorMessageOrNull()) }
                }
            }.launchIn(viewModelScope)

        // B. Discovery Content (For Recommendations/Sub-orgs)
        discoveryRepository.getChannelDiscovery(channelId)
            .onEach { response ->
                if (response is StoreReadResponse.Data) {
                    reduce { state.copy(discoveryContent = response.value) }
                }
            }.launchIn(viewModelScope)

        // C. Hot Songs (Popular)
        feedRepository.getHotSongs(channelId)
            .onEach { response ->
                if (response is StoreReadResponse.Data) {
                    reduce { state.copy(popularSongs = response.value.toImmutableList()) }
                }
            }.launchIn(viewModelScope)

        // D. Latest Videos (Feed)
        loadLatestChannelVideos()
    }

    private fun loadLatestChannelVideos() = intent {
        // We reuse the FeedRepository to fetch videos specifically for this channel.
        // We use the LATEST_STREAMS preset (sort by available_at desc).
        val filter = BrowseFilterState.create(ViewTypePreset.LATEST_STREAMS)

        feedRepository.getFeed(
            filter = filter,
            offset = 0,
            channelId = channelId, // Filter by this channel
            refresh = false // Use cache if available
        ).onEach { response ->
            when (response) {
                is StoreReadResponse.Data -> {
                    reduce { state.copy(latestVideos = response.value.toImmutableList()) }
                }
                is StoreReadResponse.Error -> {
                    // Don't block the whole screen if just the list fails, but show toast
                    postSideEffect(ChannelDetailsSideEffect.ShowToast("Failed to load videos"))
                }
                else -> {}
            }
        }.launchIn(viewModelScope)
    }
}