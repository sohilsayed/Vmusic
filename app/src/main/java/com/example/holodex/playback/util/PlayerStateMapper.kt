// File: java/com/example/holodex/playback/util/PlayerStateMapper.kt
package com.example.holodex.playback.util

import androidx.media3.common.Player
import com.example.holodex.playback.domain.model.DomainPlaybackState
import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode

object PlayerStateMapper {

    fun mapExoPlayerStateToDomain(playbackState: Int, playWhenReady: Boolean): DomainPlaybackState {
        return when (playbackState) {
            Player.STATE_IDLE -> DomainPlaybackState.IDLE
            Player.STATE_BUFFERING -> DomainPlaybackState.BUFFERING
            Player.STATE_READY -> if (playWhenReady) DomainPlaybackState.PLAYING else DomainPlaybackState.PAUSED
            Player.STATE_ENDED -> DomainPlaybackState.ENDED
            else -> DomainPlaybackState.IDLE
        }
    }
    @Player.RepeatMode
    fun mapDomainRepeatModeToExoPlayer(domainMode: DomainRepeatMode): Int {
        return when (domainMode) {
            DomainRepeatMode.NONE -> Player.REPEAT_MODE_OFF
            DomainRepeatMode.ONE -> Player.REPEAT_MODE_ONE
            DomainRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    fun mapExoPlayerRepeatModeToDomain(@Player.RepeatMode exoPlayerMode: Int): DomainRepeatMode {
        return when (exoPlayerMode) {
            Player.REPEAT_MODE_OFF -> DomainRepeatMode.NONE
            Player.REPEAT_MODE_ONE -> DomainRepeatMode.ONE
            Player.REPEAT_MODE_ALL -> DomainRepeatMode.ALL
            else -> DomainRepeatMode.NONE
        }
    }

    fun mapDomainShuffleModeToExoPlayer(domainMode: DomainShuffleMode): Boolean {
        return domainMode == DomainShuffleMode.ON
    }

    fun mapExoPlayerShuffleModeToDomain(exoPlayerShuffleEnabled: Boolean): DomainShuffleMode {
        return if (exoPlayerShuffleEnabled) DomainShuffleMode.ON else DomainShuffleMode.OFF
    }
}