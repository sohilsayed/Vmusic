// File: java\com\example\holodex\data\api\HolodexApiService.kt

package com.example.holodex.data.api

import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.PaginatedVideosResponse
import com.example.holodex.data.model.VideoSearchRequest
import com.example.holodex.data.model.discovery.ChannelDetails
import com.example.holodex.data.model.discovery.MusicdexSong
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// --- Request/Response Models ---
data class LoginRequest(val service: String, val token: String)
data class LoginResponse(val jwt: String) // Assuming user object is handled separately
data class LatestSongsRequest(
    val channel_id: String? = null,
    val paginated: Boolean = true,
    val limit: Int = 25,
    val offset: Int = 0
)

data class PaginatedSongsResponse(
    val total: String,
    val items: List<MusicdexSong>
) {
    fun getTotalAsInt(): Int? = total.toIntOrNull()
}

// Represents the structure from /statics/orgs.json
data class Organization(
    val name: String,
    @SerializedName("name_jp") val nameJp: String?,
    val short: String?
)

data class PaginatedChannelsResponse(
    @SerializedName("total") val total: String?,
    @SerializedName("items") val items: List<ChannelDetails>
) {
    fun getTotalAsInt(): Int? = total?.toIntOrNull()
}

interface HolodexApiService {

    @GET("api/v2/videos/{videoId}")
    suspend fun getVideoWithSongs(
        @Path("videoId") videoId: String,
        @Query("include") include: String = "songs,live_info,description",
        @Query("lang") lang: String = "en",
        @Query("c") comments: String? = null
    ): Response<HolodexVideoItem>

    @POST("api/v2/search/videoSearch")
    suspend fun searchVideosAdvanced(
        @Body request: VideoSearchRequest
    ): Response<PaginatedVideosResponse>

    @POST("api/v2/user/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/v2/user/refresh")
    suspend fun refreshUser(): Response<LoginResponse> // Assuming it returns a new JWT

    @GET("api/v2/channels/{channelId}")
    suspend fun getChannelDetails(@Path("channelId") channelId: String): Response<ChannelDetails>

    @GET("api/v2/channels")
    suspend fun getChannels(
        @Query("type") type: String = "vtuber",
        @Query("org") organization: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("sort") sort: String = "suborg"
    ): Response<List<ChannelDetails>>

    @GET("api/v2/songs/hot")
    suspend fun getHotSongs(
        @Query("org") organization: String? = null,
        @Query("channel_id") channelId: String? = null
    ): Response<List<MusicdexSong>>

    @POST("api/v2/songs/latest")
    suspend fun getLatestSongs(@Body request: LatestSongsRequest): Response<PaginatedSongsResponse>


    @GET("/statics/orgs.json")
    suspend fun getOrganizations(): Response<List<Organization>>

    @GET("api/v2/users/favorites")
    suspend fun getFavoriteChannels(): Response<List<FavoriteChannelApiDto>>
}