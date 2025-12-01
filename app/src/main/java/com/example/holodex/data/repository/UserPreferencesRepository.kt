package com.example.holodex.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

// Extension property to get the DataStore instance
val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val AUTOPLAY_NEXT_VIDEO = booleanPreferencesKey("autoplay_next_video")
        val SHUFFLE_ON_PLAY_START = booleanPreferencesKey("shuffle_on_play_start")
        private const val TAG = "UserPrefsRepo"
    }

    val autoplayEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[AUTOPLAY_NEXT_VIDEO] != false // Default to true (autoplay is on)
        }
        .also { Timber.d("$TAG: Observing autoplayEnabled preference.") }

    val shuffleOnPlayStartEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[SHUFFLE_ON_PLAY_START] == true // Default to false (OFF)
        }

    suspend fun setAutoplayEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTOPLAY_NEXT_VIDEO] = enabled
        }
        Timber.i("$TAG: Autoplay preference set to: $enabled")
    }
    suspend fun setShuffleOnPlayStartEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHUFFLE_ON_PLAY_START] = enabled
        }
        Timber.i("$TAG: Shuffle on play start preference set to: $enabled")
    }
}
