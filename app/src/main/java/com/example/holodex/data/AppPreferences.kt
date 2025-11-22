package com.example.holodex.data

// Moved from SettingsViewModel.kt to be globally accessible
object AppPreferenceConstants {
    // Image Quality
    const val PREF_IMAGE_QUALITY = "pref_image_quality"
    const val IMAGE_QUALITY_AUTO = "AUTO"
    const val IMAGE_QUALITY_MEDIUM = "MEDIUM"
    const val IMAGE_QUALITY_LOW = "LOW"

    // Audio Quality
    const val PREF_AUDIO_QUALITY = "pref_audio_quality"
    const val AUDIO_QUALITY_BEST = "BEST"
    const val AUDIO_QUALITY_STANDARD = "STANDARD"
    const val AUDIO_QUALITY_SAVER = "SAVER"

    // List Loading
    const val PREF_LIST_LOADING_CONFIG = "pref_list_loading_config"
    const val LIST_LOADING_NORMAL = "NORMAL"
    const val LIST_LOADING_REDUCED = "REDUCED"
    const val LIST_LOADING_MINIMAL = "MINIMAL"

    // Buffering
    const val PREF_BUFFERING_STRATEGY = "pref_buffering_strategy"
    const val BUFFERING_STRATEGY_AGGRESSIVE = "AGGRESSIVE_START"
    const val BUFFERING_STRATEGY_BALANCED = "BALANCED"
    const val BUFFERING_STRATEGY_STABLE = "STABLE_PLAYBACK"

    const val PREF_AUTOPLAY_NEXT_VIDEO = "pref_autoplay_next_video"
    const val PREF_DOWNLOAD_LOCATION = "pref_download_location_uri"
}

object ThemePreference {
    const val KEY = "app_theme_preference"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
    const val SYSTEM = "SYSTEM"
}