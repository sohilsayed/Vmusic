// File: java/com/example/holodex/data/api/AuthenticatedMusicdexApiService.kt

package com.example.holodex.data.api

import com.example.holodex.data.model.discovery.DiscoveryResponse
import com.example.holodex.data.model.discovery.FullPlaylist
import com.example.holodex.data.model.discovery.PlaylistStub
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class LikeRequest(val song_id: String)
// The incorrect DeleteLikeRequest class is removed.

data class StarPlaylistRequest(val playlist_id: String)

data class LikedSongApiDto(
    val id: String, // The song's unique ID
    val channel_id: String,
    val video_id: String,
    val name: String,
    val start: Int,
    val end: Int,
    val original_artist: String?,
    val art: String?,
    val channel: ApiChannelStub?
)

data class ApiChannelStub(
    val name: String,
    val english_name: String?,
    val photo: String?
)

data class PaginatedLikesResponse(
    val page_count: Int,
    val content: List<LikedSongApiDto>
)

data class FavoriteChannelApiDto(
    val id: String,
    val name: String? = null,
    val english_name: String? = null,
    val photo: String?,
    val org: String?,
    val twitter: String?
)
data class PatchOperation(
    val op: String, // "add" or "remove"
    val channel_id: String
)
typealias PatchFavoriteChannelsRequest = List<PatchOperation>

/**
 * Defines the authenticated endpoints for the music.holodex.net API.
 */
interface AuthenticatedMusicdexApiService {

    @GET("api/v2/musicdex/discovery/favorites")
    suspend fun getDiscoveryForFavorites(): Response<DiscoveryResponse>

    @GET("api/v2/musicdex/like")
    suspend fun getLikes(
        @Query("since") sinceTimestamp: Long? = null,
        @Query("page") page: Int? = 1,
        @Query("paginated") paginated: Boolean = true
    ): Response<PaginatedLikesResponse>

    @GET("api/v2/musicdex/like/check")
    suspend fun checkLikes(@Query("song_id") songIds: String): Response<List<Boolean>>

    @POST("api/v2/musicdex/like")
    suspend fun addLike(@Body request: LikeRequest): Response<Unit>

    // --- FIX: Change to a proper DELETE request with a body ---
    @HTTP(method = "DELETE", path = "api/v2/musicdex/like", hasBody = true)
    suspend fun deleteLike(@Body request: LikeRequest): Response<Unit>
    // --- END OF FIX ---

    @PATCH("api/v2/users/favorites")
    suspend fun patchFavoriteChannels(@Body request: PatchFavoriteChannelsRequest): Response<List<FavoriteChannelApiDto>>

    @GET("api/v2/musicdex/history/{songId}")
    suspend fun trackSongInHistory(@Path("songId") songId: String): Response<Unit>

    @GET("api/v2/musicdex/playlist/{playlistId}")
    suspend fun getPlaylistContent(@Path(value = "playlistId", encoded = true) playlistId: String): Response<FullPlaylist>

    @GET("api/v2/musicdex/playlist")
    suspend fun getMyPlaylists(): Response<List<PlaylistDto>>

    @POST("api/v2/musicdex/playlist")
    suspend fun createOrUpdatePlaylist(@Body playlist: PlaylistUpdateRequest): Response<List<PlaylistDto>>

    @DELETE("api/v2/musicdex/playlist/{playlistId}")
    suspend fun deletePlaylist(@Path("playlistId") playlistId: String): Response<Unit>

    @GET("api/v2/musicdex/playlist/{playlistId}/{songId}")
    suspend fun addSongToPlaylist(
        @Path("playlistId") playlistId: String,
        @Path("songId") songId: String
    ): Response<Unit>

    @DELETE("api/v2/musicdex/playlist/{playlistId}/{songId}")
    suspend fun removeSongFromPlaylist(
        @Path("playlistId") playlistId: String,
        @Path("songId") songId: String
    ): Response<Unit>

    @GET("api/v2/musicdex/star")
    suspend fun getStarredPlaylists(): Response<List<PlaylistStub>>

    @POST("api/v2/musicdex/star")
    suspend fun starPlaylist(@Body request: StarPlaylistRequest): Response<Unit>

    @HTTP(method = "DELETE", path = "api/v2/musicdex/star", hasBody = true)
    suspend fun unstarPlaylist(@Body request: StarPlaylistRequest): Response<Unit>
}