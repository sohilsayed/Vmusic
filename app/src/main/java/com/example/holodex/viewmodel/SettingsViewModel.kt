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
import com.example.holodex.data.AppPreferenceConstants
import com.example.holodex.data.ThemePreference
import com.example.holodex.data.download.LegacyDownloadScanner
import com.example.holodex.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import javax.inject.Inject

// --- States ---
sealed class ApiKeySaveResult {
    object Success : ApiKeySaveResult()
    object Empty : ApiKeySaveResult()
    data class Error(val message: String) : ApiKeySaveResult()
    object Idle : ApiKeySaveResult()
}

sealed class ScanStatus {
    object Idle : ScanStatus()
    object Scanning : ScanStatus()
    data class Complete(val importedCount: Int) : ScanStatus()
    data class Error(val message: String) : ScanStatus()
}

// Single Consolidated State
data class SettingsState(
    val currentApiKey: String = "",
    val apiKeySaveResult: ApiKeySaveResult = ApiKeySaveResult.Idle,
    val cacheClearStatus: String? = null,
    val scanStatus: ScanStatus = ScanStatus.Idle,
    val transientMessage: String? = null,

    // Preferences
    val currentTheme: String = ThemePreference.SYSTEM,
    val currentImageQuality: String = AppPreferenceConstants.IMAGE_QUALITY_AUTO,
    val currentAudioQuality: String = AppPreferenceConstants.AUDIO_QUALITY_BEST,
    val currentListLoadingConfig: String = AppPreferenceConstants.LIST_LOADING_NORMAL,
    val currentBufferingStrategy: String = AppPreferenceConstants.BUFFERING_STRATEGY_AGGRESSIVE,
    val downloadLocation: String = "",

    // DataStore values
    val autoplayEnabled: Boolean = true,
    val shuffleOnPlayStartEnabled: Boolean = false
)

// Side Effects
sealed class SettingsSideEffect {
    data class ShowToast(val message: String) : SettingsSideEffect()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val sharedPreferences: SharedPreferences,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workManager: WorkManager,
    private val legacyDownloadScanner: LegacyDownloadScanner
) : ContainerHost<SettingsState, SettingsSideEffect>, ViewModel() {

    override val container: Container<SettingsState, SettingsSideEffect> = container(SettingsState())

    init {
        // Initialize State from SharedPreferences
        val apiKey = sharedPreferences.getString("API_KEY", "") ?: ""
        val theme = sharedPreferences.getString(ThemePreference.KEY, ThemePreference.SYSTEM) ?: ThemePreference.SYSTEM
        val imgQuality = sharedPreferences.getString(AppPreferenceConstants.PREF_IMAGE_QUALITY, AppPreferenceConstants.IMAGE_QUALITY_AUTO) ?: AppPreferenceConstants.IMAGE_QUALITY_AUTO
        val audioQuality = sharedPreferences.getString(AppPreferenceConstants.PREF_AUDIO_QUALITY, AppPreferenceConstants.AUDIO_QUALITY_BEST) ?: AppPreferenceConstants.AUDIO_QUALITY_BEST
        val listLoading = sharedPreferences.getString(AppPreferenceConstants.PREF_LIST_LOADING_CONFIG, AppPreferenceConstants.LIST_LOADING_NORMAL) ?: AppPreferenceConstants.LIST_LOADING_NORMAL
        val buffering = sharedPreferences.getString(AppPreferenceConstants.PREF_BUFFERING_STRATEGY, AppPreferenceConstants.BUFFERING_STRATEGY_AGGRESSIVE) ?: AppPreferenceConstants.BUFFERING_STRATEGY_AGGRESSIVE
        val downloadLoc = sharedPreferences.getString(AppPreferenceConstants.PREF_DOWNLOAD_LOCATION, "") ?: ""

        intent {
            reduce {
                state.copy(
                    currentApiKey = apiKey,
                    currentTheme = theme,
                    currentImageQuality = imgQuality,
                    currentAudioQuality = audioQuality,
                    currentListLoadingConfig = listLoading,
                    currentBufferingStrategy = buffering,
                    downloadLocation = downloadLoc
                )
            }
        }

        // Observe DataStore flows
        viewModelScope.launch {
            userPreferencesRepository.autoplayEnabled.collect { enabled ->
                intent {
                    reduce { state.copy(autoplayEnabled = enabled) }
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.shuffleOnPlayStartEnabled.collect { enabled ->
                intent {
                    reduce { state.copy(shuffleOnPlayStartEnabled = enabled) }
                }
            }
        }
    }

    // --- Actions ---
    fun enqueueWork(request: androidx.work.WorkRequest) {
        workManager.enqueue(request)
    }
    fun saveApiKey(key: String) = intent {
        val trimmedKey = key.trim()
        if (trimmedKey.isBlank()) {
            reduce { state.copy(apiKeySaveResult = ApiKeySaveResult.Empty) }
            return@intent
        }
        runCatching {
            sharedPreferences.edit {
                putString("API_KEY", trimmedKey)
            }
        }.onSuccess {
            reduce { state.copy(currentApiKey = trimmedKey, apiKeySaveResult = ApiKeySaveResult.Success) }
        }.onFailure {
            reduce { state.copy(apiKeySaveResult = ApiKeySaveResult.Error("Failed to save API key.")) }
        }
    }

    fun resetApiKeySaveResult() = intent {
        reduce { state.copy(apiKeySaveResult = ApiKeySaveResult.Idle) }
    }

    fun runLegacyFileScan() = intent {
        if (state.scanStatus is ScanStatus.Scanning) return@intent
        reduce { state.copy(scanStatus = ScanStatus.Scanning) }

        runCatching {
            legacyDownloadScanner.scanAndImportLegacyDownloads()
        }.onSuccess { count ->
            reduce { state.copy(scanStatus = ScanStatus.Complete(count)) }
        }.onFailure { e ->
            reduce { state.copy(scanStatus = ScanStatus.Error("Scan failed: ${e.localizedMessage}")) }
        }
    }

    fun resetScanStatus() = intent {
        reduce { state.copy(scanStatus = ScanStatus.Idle) }
    }

    fun triggerManualSync() = intent {
        postSideEffect(SettingsSideEffect.ShowToast("Syncing account data..."))
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
        workManager.enqueueUniqueWork("ManualSync", ExistingWorkPolicy.REPLACE, request)
    }

    // --- Preferences Setters ---

    fun setThemePreference(theme: String) = intent {
        sharedPreferences.edit {
            putString(ThemePreference.KEY, theme)
        }
        reduce { state.copy(currentTheme = theme) }
    }

    fun setImageQualityPreference(quality: String) = intent {
        sharedPreferences.edit {
            putString(AppPreferenceConstants.PREF_IMAGE_QUALITY, quality)
        }
        reduce { state.copy(currentImageQuality = quality) }
    }

    fun setAudioQualityPreference(quality: String) = intent {
        sharedPreferences.edit {
            putString(AppPreferenceConstants.PREF_AUDIO_QUALITY, quality)
        }
        reduce { state.copy(currentAudioQuality = quality) }
    }

    fun setListLoadingConfigPreference(config: String) = intent {
        sharedPreferences.edit {
            putString(AppPreferenceConstants.PREF_LIST_LOADING_CONFIG, config)
        }
        reduce { state.copy(currentListLoadingConfig = config) }
    }

    fun setBufferingStrategyPreference(strategy: String) = intent {
        sharedPreferences.edit {
            putString(AppPreferenceConstants.PREF_BUFFERING_STRATEGY, strategy)
        }
        reduce { state.copy(currentBufferingStrategy = strategy) }
    }

    fun saveDownloadLocation(uri: Uri) = intent {
        runCatching {
            val contentResolver = application.contentResolver
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)

            val uriString = uri.toString()
            sharedPreferences.edit {
                putString(AppPreferenceConstants.PREF_DOWNLOAD_LOCATION, uriString)
            }
            reduce { state.copy(downloadLocation = uriString) }
        }.onFailure {
            Timber.e(it, "Failed to save download location")
            postSideEffect(SettingsSideEffect.ShowToast("Failed to save folder permission"))
        }
    }

    fun clearDownloadLocation() = intent {
        val currentUri = state.downloadLocation
        if (currentUri.isNotEmpty()) {
            try {
                val contentResolver = application.contentResolver
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.releasePersistableUriPermission(currentUri.toUri(), flags)
            } catch (e: Exception) {
                Timber.e(e, "Failed to release permission")
            }
        }
        sharedPreferences.edit {
            remove(AppPreferenceConstants.PREF_DOWNLOAD_LOCATION)
        }
        reduce { state.copy(downloadLocation = "") }
    }

    fun setAutoplayNextVideoEnabled(enabled: Boolean) = intent {
        userPreferencesRepository.setAutoplayEnabled(enabled)
        // UI updates via flow observation in init
    }

    fun setShuffleOnPlayStartEnabled(enabled: Boolean) = intent {
        userPreferencesRepository.setShuffleOnPlayStartEnabled(enabled)
        // UI updates via flow observation in init
    }

    @UnstableApi
    fun clearAllApplicationCaches() = intent {
        reduce { state.copy(cacheClearStatus = "Clearing caches...") }
        (application as? MyApp)?.clearAllAppCachesOnDemand { success ->
            // Call intent directly - it's not a suspend function
            updateCacheStatus(success)
        }
    }

    private fun updateCacheStatus(success: Boolean) = intent {
        reduce {
            state.copy(cacheClearStatus = if (success) "Success" else "Failed")
        }
    }

    fun resetCacheClearStatus() = intent {
        reduce { state.copy(cacheClearStatus = null) }
    }
}