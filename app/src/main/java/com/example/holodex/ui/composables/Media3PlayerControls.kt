package com.example.holodex.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.example.holodex.R
import com.example.holodex.playback.domain.model.DomainPlaybackProgress
import com.example.holodex.playback.domain.model.DomainRepeatMode
import com.example.holodex.playback.domain.model.DomainShuffleMode
import com.example.holodex.playback.util.formatDurationSecondsToString

@UnstableApi
@Composable
fun Media3PlayerControls(
    player: Player, // Kept only for capability checks
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: DomainPlaybackProgress,
    shuffleMode: DomainShuffleMode,
    repeatMode: DomainRepeatMode,
    isRadioMode: Boolean,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Hardcoded White aesthetic for Full Player as it's on a dark/blurred background
    val primaryColor = Color.White
    val inactiveColor = Color.White.copy(alpha = 0.5f)
    val activeTint = Color(0xFF4CAF50) // Green for active shuffle/repeat

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        // --- SEEK BAR ---
        PlayerSeekBar(
            progress = progress,
            onSeek = onSeek,
            onScrubbingChange = onScrubbingChange,
            thumbColor = primaryColor,
            activeTrackColor = primaryColor,
            inactiveTrackColor = primaryColor.copy(alpha = 0.2f),
            timeTextColor = primaryColor.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(16.dp))

        // --- CONTROLS ROW ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(
                onClick = onToggleShuffle,
                enabled = !isRadioMode && player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)
            ) {
                Icon(
                    painter = painterResource(id = if (shuffleMode == DomainShuffleMode.ON) R.drawable.ic_shuffle_on_24 else R.drawable.ic_shuffle_off_24),
                    contentDescription = "Shuffle",
                    tint = if (shuffleMode == DomainShuffleMode.ON) activeTint else inactiveColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Previous
            IconButton(
                onClick = onSkipPrevious,
                enabled = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = primaryColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            // PLAY / PAUSE / LOADING (Hero Button)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = primaryColor,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(primaryColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black, // Contrast
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Next
            IconButton(
                onClick = onSkipNext,
                enabled = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = primaryColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Repeat
            IconButton(
                onClick = onToggleRepeat,
                enabled = !isRadioMode && player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)
            ) {
                val iconRes = when (repeatMode) {
                    DomainRepeatMode.ONE -> R.drawable.ic_repeat_one_24
                    DomainRepeatMode.ALL -> R.drawable.ic_repeat_on_24
                    else -> R.drawable.ic_repeat_off_24
                }
                val tint = if (repeatMode != DomainRepeatMode.NONE) activeTint else inactiveColor
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "Repeat",
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

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

    // When the real progress updates, update the slider ONLY if the user isn't dragging it
    LaunchedEffect(progress.positionSec) {
        if (!isUserScrubbing) {
            sliderPosition = progress.positionSec.toFloat()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPosition,
            onValueChange = {
                isUserScrubbing = true
                sliderPosition = it
                onScrubbingChange(true)
            },
            onValueChangeFinished = {
                onSeek(sliderPosition.toLong())
                isUserScrubbing = false
                onScrubbingChange(false)
            },
            valueRange = 0f..(progress.durationSec.toFloat().coerceAtLeast(1f)),
            modifier = Modifier.fillMaxWidth().height(20.dp), // Reduce touch target slightly vertically
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = inactiveTrackColor,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp), // Align text with track start/end
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationSecondsToString(sliderPosition.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = timeTextColor
            )
            Text(
                text = formatDurationSecondsToString(progress.durationSec),
                style = MaterialTheme.typography.labelSmall,
                color = timeTextColor
            )
        }
    }
}