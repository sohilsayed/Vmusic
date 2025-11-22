// File: java/com/example/holodex/playback/domain/model/DomainPlaybackProgress.kt
package com.example.holodex.playback.domain.model

data class DomainPlaybackProgress(
    val positionSec: Long = 0L,
    val durationSec: Long = 0L,
    val bufferedPositionSec: Long = 0L
) {
    companion object {
        val NONE = DomainPlaybackProgress()
    }
}