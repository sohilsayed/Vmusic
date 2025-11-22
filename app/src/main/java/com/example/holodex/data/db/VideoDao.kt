package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: CachedVideoEntity)

    @Transaction
    @Query("SELECT * FROM videos WHERE id = :videoId")
    suspend fun getVideoWithSongsOnce(videoId: String): VideoWithSongs?

    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    suspend fun getVideoByIdOnce(videoId: String): CachedVideoEntity?


    @Query("DELETE FROM videos")
    fun clearAllVideos()

    @Query("DELETE FROM songs WHERE video_id = :videoId")
    suspend fun deleteSongsForVideo(videoId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<CachedSongEntity>)

    @Query("SELECT * FROM videos WHERE id IN (:videoIds)")
    fun getVideosByIds(videoIds: List<String>): Flow<List<CachedVideoEntity>>


    @Query("DELETE FROM songs")
    fun clearAllSongs()
}

