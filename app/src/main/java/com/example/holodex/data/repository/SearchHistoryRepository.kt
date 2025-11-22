// File: java/com/example/holodex/data/repository/SearchHistoryRepository.kt
package com.example.holodex.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

interface SearchHistoryRepository {
    val searchHistory: StateFlow<List<String>>
    suspend fun addSearchQueryToHistory(query: String)
    suspend fun loadSearchHistory()
    suspend fun clearSearchHistory()
}

class SharedPreferencesSearchHistoryRepository(
    private val sharedPreferences: SharedPreferences,
    private val gson: Gson,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SearchHistoryRepository {

    private companion object {
        const val PREF_SEARCH_HISTORY = "search_history_list_v1" // Moved from ViewModel
        const val MAX_SEARCH_HISTORY_SIZE = 10 // Moved from ViewModel
        const val TAG = "SearchHistoryRepo"
    }

    private val _searchHistoryFlow = MutableStateFlow<List<String>>(emptyList())
    override val searchHistory: StateFlow<List<String>> = _searchHistoryFlow.asStateFlow()

    override suspend fun loadSearchHistory() {
        withContext(ioDispatcher) {
            try {
                val historyJson = sharedPreferences.getString(PREF_SEARCH_HISTORY, null)
                _searchHistoryFlow.value = if (historyJson != null) {
                    gson.fromJson(historyJson, object : TypeToken<List<String>>() {}.type) ?: emptyList()
                } else {
                    emptyList()
                }
                Timber.tag(TAG).d("Search history loaded: ${_searchHistoryFlow.value.size} items")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load search history")
                _searchHistoryFlow.value = emptyList()
            }
        }
    }

    private suspend fun saveSearchHistory(newHistory: List<String>) {
        withContext(ioDispatcher) {
            try {
                sharedPreferences.edit { putString(PREF_SEARCH_HISTORY, gson.toJson(newHistory)) }
                Timber.tag(TAG).d("Search history saved.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to save search history")
            }
        }
    }

    override suspend fun addSearchQueryToHistory(query: String) {
        val currentHistory = _searchHistoryFlow.value.toMutableList().apply {
            remove(query) // Remove if exists to move to top
            add(0, query) // Add to the beginning
        }
        _searchHistoryFlow.value = currentHistory.take(MAX_SEARCH_HISTORY_SIZE) // Trim to max size
        saveSearchHistory(_searchHistoryFlow.value)
    }

    override suspend fun clearSearchHistory() {
        _searchHistoryFlow.value = emptyList()
        withContext(ioDispatcher) {
            sharedPreferences.edit { remove(PREF_SEARCH_HISTORY) }
        }
        Timber.tag(TAG).d("Search history cleared.")
    }
}