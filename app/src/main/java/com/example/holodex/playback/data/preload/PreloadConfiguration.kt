// File: java/com/example/holodex/playback/data/preload/PreloadConfiguration.kt
package com.example.holodex.playback.data.preload

data class PreloadConfiguration(
    val preloadDurationMs: Long = 10_000L,
    val maxConcurrentPreloads: Int = 2,
    val isEnabled: Boolean = true
)