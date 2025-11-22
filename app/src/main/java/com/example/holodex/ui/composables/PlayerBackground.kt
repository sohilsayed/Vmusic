package com.example.holodex.ui.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A highly optimized, reusable composable that displays a blurred and themed background
 * based on album artwork. It's designed for performance by minimizing overdraw and
 * caching expensive graphics objects.
 *
 * @param artworkUri The URL of the artwork to display in the background.
 * @param dynamicColor The dominant color extracted from the artwork, used for the gradient overlay.
 * @param modifier The modifier to be applied to this composable.
 */
@Composable
fun SimpleProcessedBackground(
    artworkUri: String?,
    dynamicColor: Color,
    modifier: Modifier = Modifier,
    blurRadius: Int = 80,
    saturation: Float = 0.5f,
    darkenFactor: Float = 0.7f
) {
    val animatedPrimaryColor by animateColorAsState(
        targetValue = dynamicColor,
        label = "animated_primary_color_background",
        animationSpec = tween(1200)
    )

    val colorFilter = remember(saturation, darkenFactor) {
        ColorFilter.colorMatrix(
            ColorMatrix().apply {
                setToSaturation(saturation)
                val values = floatArrayOf(
                    darkenFactor, 0f, 0f, 0f, 0f,
                    0f, darkenFactor, 0f, 0f, 0f,
                    0f, 0f, darkenFactor, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                timesAssign(ColorMatrix(values))
            }
        )
    }

    val gradientBrush = remember(animatedPrimaryColor) {
        Brush.verticalGradient(
            colors = listOf(
                animatedPrimaryColor.copy(alpha = 0.2f),
                animatedPrimaryColor.copy(alpha = 0.4f),
                Color.Black.copy(alpha = 0.7f)
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = artworkUri,
            contentDescription = "Background artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = blurRadius.dp),
            colorFilter = colorFilter
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        )
    }
}