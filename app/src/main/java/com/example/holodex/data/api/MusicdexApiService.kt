// File: java\com\example\holodex\data\api\MusicdexApiService.kt

package com.example.holodex.data.api

import com.example.holodex.data.model.discovery.DiscoveryResponse
import com.example.holodex.data.model.discovery.FullPlaylist
import com.example.holodex.data.model.discovery.MusicdexSong
import com.example.holodex.data.model.discovery.PlaylistStub
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// --- Request/Response Models ---
data class PlaylistListResponse(
    val total: Int,
    val items: List<PlaylistStub>
)

data class ElasticsearchRequest(
    val q: String = "*",
    val query_by: String,
    val sort_by: String,
    val facet_by: String,
    val page: Int = 1,
    val per_page: Int = 25
    // Add other fields as needed for advanced search
)

data class ElasticsearchResponse<T>(
    val found: Int,
    val page: Int,
    val hits: List<Hit<T>>?
)

data class Hit<T>(
    val document: T
)

/**
 * Defines the public, non-authenticated endpoints for the music.holodex.net API.
 * Requires the X-APIKEY via an interceptor.
 */
interface MusicdexApiService {

    @GET("api/v2/musicdex/discovery/org/{org}")
    suspend fun getDiscoveryForOrg(@Path("org") organization: String): Response<DiscoveryResponse>

    @GET("api/v2/musicdex/discovery/channel/{channelId}")
    suspend fun getDiscoveryForChannel(@Path("channelId") channelId: String): Response<DiscoveryResponse>

    @GET("api/v2/musicdex/playlist/{playlistId}")
    suspend fun getPlaylistContent(@Path(value = "playlistId", encoded = true) playlistId: String): Response<FullPlaylist>

    @GET("api/v2/musicdex/radio/{radioId}")
    suspend fun getRadioContent(
        @Path("radioId") radioId: String,
        @Query("offset") offset: Int = 0
    ): Response<FullPlaylist>

    @GET("api/v2/musicdex/discovery/org/{org}/playlists")
    suspend fun getOrgPlaylists(
        @Path("org") org: String,
        @Query("type") type: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): Response<PlaylistListResponse>


    // --- NEW ENDPOINT for Goal 2.1 ---
    @POST("api/v2/musicdex/elasticsearch/search")
    suspend fun searchElasticsearch(
        @Body request: ElasticsearchRequest
    ): Response<ElasticsearchResponse<MusicdexSong>>
}