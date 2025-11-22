// File: java/com/example/holodex/data/db/HistoryDao.kt (MODIFIED)
package com.example.holodex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_items ORDER BY playedAtTimestamp DESC")
    fun getHistory(): Flow<List<HistoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItemEntity)

    @Query("DELETE FROM history_items WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Transaction
    suspend fun upsert(item: HistoryItemEntity) {
        deleteByItemId(item.itemId)
        insert(item)
    }

    @Query("DELETE FROM history_items")
    suspend fun clearAll()


}