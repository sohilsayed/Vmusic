// File: java/com/example/holodex/playback/domain/model/DomainPlaybackState.kt
package com.example.holodex.playback.domain.model

enum class DomainPlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}