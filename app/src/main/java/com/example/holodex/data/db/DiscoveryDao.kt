package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiscoveryDao {
    @Query("SELECT * FROM cached_discovery_responses WHERE pageKey = :pageKey")
    suspend fun getResponse(pageKey: String): CachedDiscoveryResponse?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponse(response: CachedDiscoveryResponse)

    @Query("DELETE FROM cached_discovery_responses WHERE timestamp < :expiredTime")
    suspend fun deleteExpiredResponses(expiredTime: Long)
}