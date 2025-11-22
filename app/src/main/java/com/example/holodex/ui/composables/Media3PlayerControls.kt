@file:UnstableApi

package com.example.holodex.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.holodex.R
import com.example.holodex.playback.domain.model.DomainPlaybackProgress
import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.util.formatDurationSecondsToString

private const val CONTROLS_TAG = "Media3PlayerControls"

/**
 * A self-contained composable that displays a full set of player controls,
 * powered by Media3's Compose state holders.
 *
 * @param player The Media3 Player instance.
 * @param progress The current playback progress, passed from the ViewModel to display on the seek bar.
 * @param onSeek A lambda to be invoked when the user interacts with the seek bar.
 * @param primaryColor The primary theme color, used for prominent elements like the play button.
 * @param onPrimaryColor The color for icons and text that appear on the primary color, used for other controls.
 */
@Composable
fun Media3PlayerControls(
    // The player is now ONLY for reading state for button enabled/disabled status
    player: Player,
    shuffleMode: DomainShuffleMode,
    repeatMode: DomainRepeatMode,
    progress: DomainPlaybackProgress,
    isRadioMode: Boolean,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    primaryColor: Color,
    onPrimaryColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PlayerSeekBar(
            progress = progress,
            onSeek = onSeek,
            onScrubbingChange = onScrubbingChange,
            thumbColor = primaryColor,
            activeTrackColor = onPrimaryColor,
            inactiveTrackColor = onPrimaryColor.copy(alpha = 0.3f),
            timeTextColor = onPrimaryColor.copy(alpha = 0.7f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- CUSTOM SHUFFLE BUTTON ---
            val isShuffleOn = shuffleMode == DomainShuffleMode.ON // Use the provided state
            IconButton(
                onClick = onToggleShuffle,
                enabled = player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE) && !isRadioMode
            ) {
                Icon(
                    painter = painterResource(id = if (isShuffleOn) R.drawable.ic_shuffle_on_24 else R.drawable.ic_shuffle_off_24),
                    contentDescription = stringResource(R.string.action_shuffle),
                    modifier = Modifier.size(28.dp),
                    tint = if (isShuffleOn) primaryColor else onPrimaryColor.copy(alpha = 0.6f)
                )
            }

            // --- CUSTOM PREVIOUS BUTTON ---
            IconButton(
                onClick = onSkipPrevious,
                enabled = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.action_previous),
                    modifier = Modifier.size(36.dp),
                    tint = onPrimaryColor
                )
            }

            // --- CUSTOM PLAY/PAUSE BUTTON ---
            IconButton(
                onClick = onPlayPause,
                enabled = player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
            ) {
                Icon(
                    imageVector = if (player.isPlaying) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                    contentDescription = if (player.isPlaying) stringResource(R.string.action_pause) else stringResource(
                        R.string.action_play
                    ),
                    modifier = Modifier.size(64.dp),
                    tint = primaryColor
                )
            }

            // --- CUSTOM NEXT BUTTON ---
            IconButton(
                onClick = onSkipNext,
                enabled = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.action_next),
                    modifier = Modifier.size(36.dp),
                    tint = onPrimaryColor
                )
            }

            // --- CUSTOM REPEAT BUTTON ---
            IconButton(
                onClick = onToggleRepeat,
                enabled = player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE) && !isRadioMode
            ) {
                val iconRes = when (repeatMode) { // Use the provided state
                    DomainRepeatMode.ONE -> R.drawable.ic_repeat_one_24
                    DomainRepeatMode.ALL -> R.drawable.ic_repeat_on_24
                    else -> R.drawable.ic_repeat_off_24
                }
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = stringResource(R.string.action_repeat),
                    modifier = Modifier.size(28.dp),
                    tint = if (repeatMode != DomainRepeatMode.NONE) primaryColor else onPrimaryColor.copy(
                        alpha = 0.6f
                    )
                )
            }
        }
    }
}


// ===================================================================
// PRIVATE, INTERNAL BUILDING BLOCKS FOR THE CONTROLS
// ===================================================================

@Composable
private fun PlayerSeekBar(
    progress: DomainPlaybackProgress,
    onSeek: (Long) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    thumbColor: Color,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    timeTextColor: Color
) {
    var sliderPosition by remember(progress.positionSec) { mutableFloatStateOf(progress.positionSec.toFloat()) }
    var isUserScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(isUserScrubbing) {
        onScrubbingChange(isUserScrubbing)
    }

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = if (isUserScrubbing) sliderPosition else progress.positionSec.toFloat(),
            onValueChange = {
                isUserScrubbing = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                onSeek(sliderPosition.toLong())
                isUserScrubbing = false
            },
            valueRange = 0f..(progress.durationSec.toFloat().coerceAtLeast(1f)),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = inactiveTrackColor
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationSecondsToString(if (isUserScrubbing) sliderPosition.toLong() else progress.positionSec),
                style = MaterialTheme.typography.bodySmall,
                color = timeTextColor
            )
            Text(
                text = formatDurationSecondsToString(progress.durationSec),
                style = MaterialTheme.typography.bodySmall,
                color = timeTextColor
            )
        }
    }
}

