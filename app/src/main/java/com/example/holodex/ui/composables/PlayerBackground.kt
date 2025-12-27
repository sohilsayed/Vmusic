package com.example.holodex.ui.composables

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Premium Player Background.
 *
 * Strategy:
 * 1. Android 12+: Uses hardware-accelerated RenderEffect (Modifier.blur) for a true frosted glass look.
 * 2. Older Android: Uses a dynamic gradient mesh derived from the artwork color.
 *    This avoids the "pixelated/blocky" look of upscaling small images.
 */
@Composable
fun SimpleProcessedBackground(
    artworkUri: String?,
    dynamicColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Animate the color when song changes
    val animatedColor by animateColorAsState(
        targetValue = dynamicColor,
        label = "bg_color",
        animationSpec = tween(1500)
    )

    // Create a subtle "breathing" animation for the gradient
    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_shift"
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // --- STRATEGY A: Modern Android (True Blur) ---
            // Load a medium-res image and apply a heavy hardware blur.
            // This looks like "Frosted Glass".
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(400, 400) // Medium size is enough for heavy blur
                    .crossfade(true)
                    .allowHardware(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 100.dp), // Hardware blur
                alpha = 0.6f
            )

            // Tint overlay
            Box(modifier = Modifier
                .fillMaxSize()
                .background(animatedColor.copy(alpha = 0.2f))
            )

        } else {
            // --- STRATEGY B: Legacy / Performance (Gradient Mesh) ---
            // Instead of a bad pixelated image, we draw a beautiful gradient
            // using the extracted color. This is faster and looks cleaner.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        // Create a multi-stop gradient based on the extracted color
                        val gradient = Brush.radialGradient(
                            colors = listOf(
                                animatedColor.copy(alpha = 0.4f),
                                animatedColor.copy(alpha = 0.15f),
                                Color.Black
                            ),
                            center = Offset(
                                x = size.width * 0.5f,
                                y = size.height * 0.4f + (gradientShift * 0.1f) // Subtle movement
                            ),
                            radius = size.width * 1.2f,
                            tileMode = TileMode.Clamp
                        )
                        onDrawBehind {
                            drawRect(gradient)
                        }
                    }
            )
        }

        // --- FINAL LAYER: Readability Scrim ---
        // Ensures white text/buttons are always visible at the bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val scrim = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,       // Top (Art visible)
                            Color.Black.copy(0.2f),  // Middle
                            Color.Black.copy(0.85f), // Controls Area (Dark)
                            Color.Black.copy(0.95f)  // Navigation Bar (Very Dark)
                        ),
                        startY = size.height * 0.4f
                    )
                    onDrawBehind {
                        drawRect(scrim)
                    }
                }
        )
    }
}