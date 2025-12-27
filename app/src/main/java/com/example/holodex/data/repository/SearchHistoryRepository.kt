package com.example.holodex.data.repository

import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {
    // FIX: Change StateFlow -> Flow to match Room DAO
    val searchHistory: Flow<List<String>>

    suspend fun addSearchQueryToHistory(query: String)
    suspend fun loadSearchHistory()
    suspend fun clearSearchHistory()
}