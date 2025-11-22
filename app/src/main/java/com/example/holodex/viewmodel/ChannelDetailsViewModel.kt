package com.example.holodex.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.holodex.data.db.ExternalChannelEntity
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.model.discovery.DiscoveryResponse
import com.example.holodex.data.repository.HolodexRepository
import com.example.holodex.data.repository.LocalRepository
import com.example.holodex.util.DynamicTheme
import com.example.holodex.util.PaletteExtractor
import com.example.holodex.viewmodel.mappers.toUnifiedDisplayItem
import com.example.holodex.viewmodel.mappers.toVideoShell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import org.schabi.newpipe.extractor.Page
import javax.inject.Inject

// --- State ---
data class ChannelDetailsState(
    val isExternal: Boolean = false,
    val channelDetails: ChannelDetails? = null,
    val dynamicTheme: DynamicTheme = DynamicTheme.default(Color.Black, Color.White),

    // Holodex Data
    val discoveryContent: DiscoveryResponse? = null,
    val popularSongs: List<UnifiedDisplayItem> = emptyList(),

    // External Data
    val externalMusicItems: List<UnifiedDisplayItem> = emptyList(),
    val nextPageCursor: Page? = null,
    val isLoadingMore: Boolean = false,
    val endOfList: Boolean = false,

    // Screen Status
    val isLoading: Boolean = true,
    val error: String? = null
)

// --- Side Effects ---
sealed class ChannelDetailsSideEffect {
    data class ShowToast(val message: String) : ChannelDetailsSideEffect()
}

@HiltViewModel
class ChannelDetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val holodexRepository: HolodexRepository,
    private val localRepository: LocalRepository,
    private val paletteExtractor: PaletteExtractor
) : ContainerHost<ChannelDetailsState, ChannelDetailsSideEffect>, ViewModel() {

    companion object {
        const val CHANNEL_ID_ARG = "channelId"
    }

    val channelId: String = savedStateHandle.get<String>(CHANNEL_ID_ARG) ?: ""

    override val container: Container<ChannelDetailsState, ChannelDetailsSideEffect> = container(ChannelDetailsState()) {
        if (channelId.isNotBlank()) {
            initializeChannel()
        } else {
            // FIX: Wrapped the reduce call in an intent block
            intent {
                reduce { state.copy(isLoading = false, error = "Invalid Channel ID") }
            }
        }
    }

    private fun initializeChannel() = intent {
        reduce { state.copy(isLoading = true, error = null) }

        // Check if external first
        val externalChannel = localRepository.getExternalChannel(channelId)

        if (externalChannel != null) {
            loadExternalChannel(externalChannel)
        } else {
            loadHolodexChannel()
        }
    }

    private suspend fun loadExternalChannel(channel: ExternalChannelEntity) = intent {
        val details = ChannelDetails(
            id = channel.channelId,
            name = channel.name,
            englishName = channel.name,
            description = "Music from this channel is sourced directly from YouTube.",
            photoUrl = channel.photoUrl,
            bannerUrl = null, org = "External", suborg = null, twitter = null, group = null
        )

        val theme = paletteExtractor.extractThemeFromUrl(channel.photoUrl, DynamicTheme.default(Color.Black, Color.White))

        reduce {
            state.copy(
                isExternal = true,
                channelDetails = details,
                dynamicTheme = theme
            )
        }

        // Load initial music
        loadMoreExternalMusic(isInitial = true)
    }

    private suspend fun loadHolodexChannel() = intent {
        coroutineScope {
            val detailsDeferred = async { holodexRepository.getChannelDetails(channelId) }
            val discoveryDeferred = async { holodexRepository.getDiscoveryForChannel(channelId) }
            val popularDeferred = async { holodexRepository.getHotSongsForCarousel(channelId) }

            val detailsResult = detailsDeferred.await()
            val discoveryResult = discoveryDeferred.await()
            val popularResult = popularDeferred.await()

            if (detailsResult.isSuccess) {
                val details = detailsResult.getOrThrow()
                val theme = paletteExtractor.extractThemeFromUrl(details.bannerUrl, DynamicTheme.default(Color.Black, Color.White))

                val discovery = discoveryResult.getOrNull()
                val popular = popularResult.getOrNull()?.map { song ->
                    val videoShell = song.toVideoShell()
                    song.toUnifiedDisplayItem(parentVideo = videoShell, isLiked = false, isDownloaded = false)
                } ?: emptyList()

                reduce {
                    state.copy(
                        isExternal = false,
                        isLoading = false,
                        channelDetails = details,
                        dynamicTheme = theme,
                        discoveryContent = discovery,
                        popularSongs = popular
                    )
                }
            } else {
                val error = detailsResult.exceptionOrNull()?.localizedMessage ?: "Failed to load channel."
                reduce { state.copy(isLoading = false, error = error) }
            }
        }
    }

    fun loadMoreExternalMusic(isInitial: Boolean = false) = intent {
        if (!isInitial && (state.isLoadingMore || state.endOfList)) return@intent

        reduce { state.copy(isLoadingMore = true) }

        val cursor = if (isInitial) null else state.nextPageCursor

        val result = holodexRepository.getMusicFromExternalChannel(channelId, cursor)

        result.onSuccess { fetcherResult ->
            val newItems = fetcherResult.data.map { it.toUnifiedDisplayItem(isLiked = false, downloadedSegmentIds = emptySet()) }
            val nextCursor = fetcherResult.nextPageCursor as? Page

            reduce {
                val currentList = if (isInitial) emptyList() else state.externalMusicItems
                state.copy(
                    externalMusicItems = currentList + newItems,
                    nextPageCursor = nextCursor,
                    endOfList = nextCursor == null,
                    isLoading = false, // Clear main loading if this was initial
                    isLoadingMore = false
                )
            }
        }.onFailure {
            postSideEffect(ChannelDetailsSideEffect.ShowToast("Failed to load music"))
            reduce { state.copy(isLoading = false, isLoadingMore = false, error = if(isInitial) it.message else state.error) }
        }
    }
}