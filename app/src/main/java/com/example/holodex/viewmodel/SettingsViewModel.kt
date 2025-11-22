package com.example.holodex.viewmodel

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.holodex.MyApp
import com.example.holodex.background.SyncWorker
import com.example.holodex.data.download.LegacyDownloadScanner
import com.example.holodex.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class ApiKeySaveResult {
    object Success : ApiKeySaveResult()
    object Empty : ApiKeySaveResult()
    data class Error(val message: String) : ApiKeySaveResult()
    object Idle : ApiKeySaveResult()
}

object ThemePreference {
    const val KEY = "app_theme_preference"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
    const val SYSTEM = "SYSTEM"
}

// Centralized preference constants
object AppPreferenceConstants {
    // Image Quality
    const val PREF_IMAGE_QUALITY = "pref_image_quality"
    const val IMAGE_QUALITY_AUTO = "AUTO"
    const val IMAGE_QUALITY_MEDIUM = "MEDIUM"
    const val IMAGE_QUALITY_LOW = "LOW"

    // Audio Quality (for NewPipeExtractor)
    const val PREF_AUDIO_QUALITY = "pref_audio_quality"
    const val AUDIO_QUALITY_BEST = "BEST"
    const val AUDIO_QUALITY_STANDARD = "STANDARD"
    const val AUDIO_QUALITY_SAVER = "SAVER"

    // Paging Configuration (Data Loading Intensity for Lists)
    const val PREF_LIST_LOADING_CONFIG = "pref_list_loading_config"
    const val LIST_LOADING_NORMAL = "NORMAL"
    const val LIST_LOADING_REDUCED = "REDUCED"
    const val LIST_LOADING_MINIMAL = "MINIMAL"

    // ExoPlayer Buffering Strategy
    const val PREF_BUFFERING_STRATEGY = "pref_buffering_strategy"
    const val BUFFERING_STRATEGY_AGGRESSIVE = "AGGRESSIVE_START"
    const val BUFFERING_STRATEGY_BALANCED = "BALANCED"
    const val BUFFERING_STRATEGY_STABLE = "STABLE_PLAYBACK"

    const val PREF_AUTOPLAY_NEXT_VIDEO = "pref_autoplay_next_video"
    const val PREF_DOWNLOAD_LOCATION = "pref_download_location_uri"

}
sealed class ScanStatus {
    object Idle : ScanStatus()
    object Scanning : ScanStatus()
    data class Complete(val importedCount: Int) : ScanStatus()
    data class Error(val message: String) : ScanStatus()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val sharedPreferences: SharedPreferences,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workManager: WorkManager,
    private val legacyDownloadScanner: LegacyDownloadScanner

) : ViewModel() {

    private val _apiKeySaveResult = MutableStateFlow<ApiKeySaveResult>(ApiKeySaveResult.Idle)
    val apiKeySaveResult: StateFlow<ApiKeySaveResult> = _apiKeySaveResult.asStateFlow()

    private val _currentApiKey = MutableStateFlow<String>(loadInitialApiKey())
    val currentApiKey: StateFlow<String> = _currentApiKey.asStateFlow()

    private val _cacheClearStatus = MutableStateFlow<String?>(null)
    val cacheClearStatus: StateFlow<String?> = _cacheClearStatus.asStateFlow()

    private val _currentThemePreference = MutableStateFlow(loadThemePreference())
    val currentThemePreference: StateFlow<String> = _currentThemePreference.asStateFlow()

    private val _currentImageQuality = MutableStateFlow(loadImageQualityPreference())
    val currentImageQuality: StateFlow<String> = _currentImageQuality.asStateFlow()

    private val _currentAudioQuality = MutableStateFlow(loadAudioQualityPreference())
    val currentAudioQuality: StateFlow<String> = _currentAudioQuality.asStateFlow()

    private val _currentListLoadingConfig = MutableStateFlow(loadListLoadingConfigPreference())
    val currentListLoadingConfig: StateFlow<String> = _currentListLoadingConfig.asStateFlow()

    private val _currentBufferingStrategy = MutableStateFlow(loadBufferingStrategyPreference())
    val currentBufferingStrategy: StateFlow<String> = _currentBufferingStrategy.asStateFlow()

    private val _downloadLocation = MutableStateFlow(loadDownloadLocation())
    val downloadLocation: StateFlow<String> = _downloadLocation.asStateFlow()

    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    val autoplayNextVideoEnabled: StateFlow<Boolean> = userPreferencesRepository.autoplayEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = true // Default value should match what's in UserPreferencesRepository
        )
    val shuffleOnPlayStartEnabled: StateFlow<Boolean> = userPreferencesRepository.shuffleOnPlayStartEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false // Matches the repository default
        )

    init {
        Timber.d("SettingsViewModel initialized.")
        // The collectLatest below is now redundant for the autoplayNextVideoEnabled flow itself,
        // as stateIn already makes it hot and provides the latest value.
        // It could be kept if you needed to perform side-effects *every time* the preference changes
        // within the ViewModel's scope, but usually, the UI collects it directly.
        /*
        viewModelScope.launch {
            userPreferencesRepository.autoplayEnabled.collectLatest { enabled ->
                Timber.d("SettingsViewModel: Autoplay preference updated to $enabled.")
            }
        }
        */
    }
    fun runLegacyFileScan() {
        if (_scanStatus.value is ScanStatus.Scanning) return // Prevent multiple scans

        viewModelScope.launch {
            _scanStatus.value = ScanStatus.Scanning
            try {
                val count = legacyDownloadScanner.scanAndImportLegacyDownloads()
                _scanStatus.value = ScanStatus.Complete(count)
            } catch (e: Exception) {
                Timber.e(e, "Legacy scan failed in ViewModel")
                _scanStatus.value = ScanStatus.Error("Scan failed: ${e.localizedMessage}")
            }
        }
    }

    fun resetScanStatus() {
        _scanStatus.value = ScanStatus.Idle
    }
    fun triggerManualSync() {
        viewModelScope.launch {
            Timber.i("SettingsViewModel: Manual sync triggered by user.")
            _transientMessage.emit("Syncing account data...") // Use the existing message flow for feedback

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val immediateSyncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            // Enqueue as unique work with REPLACE to ensure it runs now,
            // even if a periodic one was scheduled soon.
            workManager.enqueueUniqueWork(
                "ManualSync",
                ExistingWorkPolicy.REPLACE,
                immediateSyncRequest
            )
        }
    }
    private val _transientMessage = MutableStateFlow<String?>(null)
    val transientMessage: StateFlow<String?> = _transientMessage.asStateFlow()

    fun clearTransientMessage() {
        _transientMessage.value = null
    }
    private fun loadDownloadLocation(): String {
        return sharedPreferences.getString(AppPreferenceConstants.PREF_DOWNLOAD_LOCATION, "") ?: ""
    }

    fun saveDownloadLocation(uri: Uri) {
        viewModelScope.launch {
            try {
                // Take persistent permission to access the folder
                val contentResolver = application.contentResolver
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)

                val uriString = uri.toString()
                sharedPreferences.edit { putString(AppPreferenceConstants.PREF_DOWNLOAD_LOCATION, uriString) }
                _downloadLocation.value = uriString
                Timber.i("Saved new download location URI: $uriString")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save download location permission or preference.")
                // Optionally emit an error to a snackbar/toast
            }
        }
    }

    fun clearDownloadLocation() {
        viewModelScope.launch {
            val currentUriString = _downloadLocation.value
            if (currentUriString.isNotEmpty()) {
                try {
                    val uri = currentUriString.toUri()
                    val contentResolver = application.contentResolver
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.releasePersistableUriPermission(uri, flags)
                    Timber.i("Released persistable URI permission for: $currentUriString")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to release persistable URI permission.")
                }
            }
            sharedPreferences.edit { remove(AppPreferenceConstants.PREF_DOWNLOAD_LOCATION) }
            _downloadLocation.value = ""
        }
    }

    private fun loadInitialApiKey(): String {
        return sharedPreferences.getString("API_KEY", "") ?: ""
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            val trimmedKey = key.trim()
            if (trimmedKey.isBlank()) {
                _apiKeySaveResult.value = ApiKeySaveResult.Empty
                return@launch
            }
            try {
                sharedPreferences.edit { putString("API_KEY", trimmedKey) }
                _currentApiKey.value = trimmedKey
                _apiKeySaveResult.value = ApiKeySaveResult.Success
            } catch (e: Exception) {
                Timber.tag("SettingsViewModel").e(e, "Error saving API key")
                _apiKeySaveResult.value = ApiKeySaveResult.Error("Failed to save API key.")
            }
        }
    }

    fun resetApiKeySaveResult() {
        _apiKeySaveResult.value = ApiKeySaveResult.Idle
    }

    @UnstableApi
    fun clearAllApplicationCaches() {
        _cacheClearStatus.value = "Clearing caches..."
        (application as? MyApp)?.clearAllAppCachesOnDemand { success ->
            viewModelScope.launch {
                if (success) {
                    _cacheClearStatus.value = "All caches cleared successfully."
                    Timber.i("All caches cleared successfully reported by MyApp.")
                } else {
                    _cacheClearStatus.value = "Failed to clear all caches."
                    Timber.e("Cache clearing failed as reported by MyApp.")
                }
            }
        } ?: run {
            viewModelScope.launch {
                _cacheClearStatus.value = "Error: Could not access application to clear caches."
                Timber.e("Application context is not MyApp instance or is null.")
            }
        }
    }

    fun resetCacheClearStatus() {
        _cacheClearStatus.value = null
    }

    private fun loadThemePreference(): String {
        return sharedPreferences.getString(ThemePreference.KEY, ThemePreference.SYSTEM)
            ?: ThemePreference.SYSTEM
    }

    fun setThemePreference(themeValue: String) {
        if (themeValue !in listOf(
                ThemePreference.LIGHT,
                ThemePreference.DARK,
                ThemePreference.SYSTEM
            )
        ) {
            Timber.w("Invalid theme value set: $themeValue. Defaulting to SYSTEM.")
            _currentThemePreference.value = ThemePreference.SYSTEM
            sharedPreferences.edit { putString(ThemePreference.KEY, ThemePreference.SYSTEM) }
            return
        }
        viewModelScope.launch {
            _currentThemePreference.value = themeValue
            sharedPreferences.edit {
                putString(ThemePreference.KEY, themeValue)
            }
            Timber.d("Theme preference saved: $themeValue")
        }
    }

    private fun loadImageQualityPreference(): String {
        return sharedPreferences.getString(
            AppPreferenceConstants.PREF_IMAGE_QUALITY,
            AppPreferenceConstants.IMAGE_QUALITY_AUTO
        ) ?: AppPreferenceConstants.IMAGE_QUALITY_AUTO
    }

    fun setImageQualityPreference(quality: String) {
        viewModelScope.launch {
            _currentImageQuality.value = quality
            sharedPreferences.edit { putString(AppPreferenceConstants.PREF_IMAGE_QUALITY, quality) }
            Timber.d("Image quality preference saved: $quality. App restart may be needed for full effect on existing Coil cache policies.")
        }
    }

    private fun loadAudioQualityPreference(): String {
        return sharedPreferences.getString(
            AppPreferenceConstants.PREF_AUDIO_QUALITY,
            AppPreferenceConstants.AUDIO_QUALITY_BEST
        ) ?: AppPreferenceConstants.AUDIO_QUALITY_BEST
    }

    fun setAudioQualityPreference(quality: String) {
        viewModelScope.launch {
            _currentAudioQuality.value = quality
            sharedPreferences.edit { putString(AppPreferenceConstants.PREF_AUDIO_QUALITY, quality) }
            Timber.d("Audio quality preference saved: $quality")
        }
    }

    // NEW: Function to set autoplay preference
    fun setAutoplayNextVideoEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoplayEnabled(enabled)
            Timber.d("Autoplay next video preference set to: $enabled")
        }
    }
    fun setShuffleOnPlayStartEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShuffleOnPlayStartEnabled(enabled)
        }
    }
    private fun loadListLoadingConfigPreference(): String {
        return sharedPreferences.getString(
            AppPreferenceConstants.PREF_LIST_LOADING_CONFIG,
            AppPreferenceConstants.LIST_LOADING_NORMAL
        ) ?: AppPreferenceConstants.LIST_LOADING_NORMAL
    }

    fun setListLoadingConfigPreference(config: String) {
        viewModelScope.launch {
            _currentListLoadingConfig.value = config
            sharedPreferences.edit {
                putString(
                    AppPreferenceConstants.PREF_LIST_LOADING_CONFIG,
                    config
                )
            }
            Timber.d("List loading config saved: $config. HolodexRepository will need to react or app restart needed.")
        }
    }

    private fun loadBufferingStrategyPreference(): String {
        return sharedPreferences.getString(
            AppPreferenceConstants.PREF_BUFFERING_STRATEGY,
            AppPreferenceConstants.BUFFERING_STRATEGY_AGGRESSIVE
        ) ?: AppPreferenceConstants.BUFFERING_STRATEGY_AGGRESSIVE
    }

    fun setBufferingStrategyPreference(strategy: String) {
        viewModelScope.launch {
            _currentBufferingStrategy.value = strategy
            sharedPreferences.edit {
                putString(
                    AppPreferenceConstants.PREF_BUFFERING_STRATEGY,
                    strategy
                )
            }
            Timber.d("Buffering strategy saved: $strategy. ExoPlayer will need to be re-created or reconfigured.")
        }
    }
}