package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: DownloadedItemEntity)

    @Query("SELECT * FROM downloaded_items WHERE videoId = :videoId LIMIT 1")
    suspend fun getById(videoId: String): DownloadedItemEntity?

    @Query("SELECT * FROM downloaded_items WHERE videoId = :videoId LIMIT 1")
    fun getDownloadByIdFlow(videoId: String): Flow<DownloadedItemEntity?>

    @Query("SELECT * FROM downloaded_items ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedItemEntity>>

    @Query("SELECT * FROM downloaded_items WHERE videoId IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<DownloadedItemEntity>

    @Query("DELETE FROM downloaded_items WHERE videoId = :videoId")
    suspend fun deleteById(videoId: String)

    @Query("UPDATE downloaded_items SET downloadStatus = :status, localFileUri = :uri, fileName = :fileName, downloadedAt = :timestamp, progress = 100 WHERE videoId = :videoId")
    suspend fun updateStatusToCompleted(
        videoId: String,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        uri: String,
        fileName: String,
        timestamp: Long
    )

    @Query("UPDATE downloaded_items SET downloadStatus = :status WHERE videoId = :videoId")
    suspend fun updateStatus(videoId: String, status: DownloadStatus)

    @Query("UPDATE downloaded_items SET progress = :progress WHERE videoId = :videoId")
    suspend fun updateProgress(videoId: String, progress: Int)

    // FIX: Change parameter name from `itemId` to `videoId` for consistency with the query column.
    // This is a minor readability improvement, not a functional change.
    @Query("UPDATE downloaded_items SET progress = :progress, downloadStatus = :status WHERE videoId = :videoId")
    suspend fun updateProgressAndStatus(videoId: String, progress: Int, status: DownloadStatus)

    // FIX: Change parameter name from `itemId` to `videoId` for consistency.
    @Query("UPDATE downloaded_items SET localFileUri = :uri WHERE videoId = :videoId")
    suspend fun updateLocalFileUri(videoId: String, uri: String)

    // FIX: Change parameter name from `itemId` to `videoId` for consistency.
    @Query("UPDATE downloaded_items SET downloadedAt = :timestamp WHERE videoId = :videoId")
    suspend fun updateDownloadedAt(videoId: String, timestamp: Long)
}