package com.example.holodex.data.repository

import com.example.holodex.data.api.AuthenticatedMusicdexApiService
import com.example.holodex.data.api.MusicdexApiService
import com.example.holodex.data.model.discovery.DiscoveryResponse
import kotlinx.coroutines.flow.Flow
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.MemoryPolicy
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

@Singleton
class DiscoveryRepository @Inject constructor(
    private val musicApi: MusicdexApiService,
    private val authApi: AuthenticatedMusicdexApiService
) {
    private val store = StoreBuilder.from(
        fetcher = Fetcher.of { key: String ->
            if (key == "favorites") {
                val res = authApi.getDiscoveryForFavorites()
                if (!res.isSuccessful) throw Exception("Auth API Error")
                res.body()!!
            } else if (key.startsWith("channel_")) {
                val channelId = key.removePrefix("channel_")
                val res = musicApi.getDiscoveryForChannel(channelId)
                if (!res.isSuccessful) throw Exception("Channel Discovery API Error")
                res.body()!!
            } else {
                val org = key.removePrefix("org_")
                val res = musicApi.getDiscoveryForOrg(org)
                if (!res.isSuccessful) throw Exception("Music API Error")
                res.body()!!
            }
        }
    )
        .cachePolicy(
            MemoryPolicy.builder<Any, Any>().setExpireAfterWrite(30.minutes).build()
        )
        .build()

    fun getDiscovery(org: String): Flow<StoreReadResponse<DiscoveryResponse>> {
        val key = if(org == "Favorites") "favorites" else "org_$org"
        return store.stream(StoreReadRequest.cached(key, refresh = false))
    }

    fun getChannelDiscovery(channelId: String): Flow<StoreReadResponse<DiscoveryResponse>> {
        return store.stream(StoreReadRequest.cached("channel_$channelId", refresh = false))
    }
}