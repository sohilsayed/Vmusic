package com.example.holodex.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.ColorInt
import androidx.collection.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

// Data class to hold the extracted theme colors - keeping original structure
data class DynamicTheme(
    val primary: Color,
    val onPrimary: Color,
) {
    companion object {
        fun default(defaultPrimary: Color, defaultOnPrimary: Color) = DynamicTheme(
            primary = defaultPrimary,
            onPrimary = defaultOnPrimary
        )
    }
}

/**
 * Extracts a color palette from a given image URL with enhanced blur processing and in-memory caching.
 */
class PaletteExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = LruCache<String, DynamicTheme>(20) // Keep original cache size

    suspend fun extractThemeFromUrl(
        imageUrl: String?,
        defaultTheme: DynamicTheme
    ): DynamicTheme = withContext(Dispatchers.Default) {
        if (imageUrl.isNullOrBlank()) return@withContext defaultTheme

        // Return from cache if available
        cache.get(imageUrl)?.let {
            Timber.d("PaletteExtractor: Cache HIT for $imageUrl")
            return@withContext it
        }

        Timber.d("PaletteExtractor: Cache MISS for $imageUrl. Processing.")
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(Size(256, 256)) // Slightly larger for better blur quality while keeping it fast
                .allowHardware(false) // Palette requires software bitmaps
                .memoryCacheKey("${imageUrl}_palette_enhanced")
                .build()

            val result = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap ?: return@withContext defaultTheme

            // Create a more beautiful blurred version of the bitmap for better color extraction
            val enhancedBitmap = createEnhancedBitmap(bitmap)

            val palette = Palette.from(enhancedBitmap).generate()

            val swatch = palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.dominantSwatch
                ?: palette.mutedSwatch
                ?: palette.lightMutedSwatch
                ?: palette.darkMutedSwatch
                ?: return@withContext defaultTheme

            val primaryColor = Color(swatch.rgb)
            val onPrimaryColor = Color(getBestTextColorForBackground(swatch.rgb, Color.White.toArgb(), Color.Black.toArgb()))

            val newTheme = DynamicTheme(primary = primaryColor, onPrimary = onPrimaryColor)
            cache.put(imageUrl, newTheme) // Store in cache
            return@withContext newTheme

        } catch (e: Exception) {
            Timber.e(e, "Failed to extract palette from URL: $imageUrl")
            return@withContext defaultTheme
        }
    }

    /**
     * Creates an enhanced version of the bitmap with better color saturation and slight blur
     * for more beautiful color extraction without affecting the original API
     */
    private fun createEnhancedBitmap(originalBitmap: Bitmap): Bitmap {
        return try {
            val config = originalBitmap.config ?: Bitmap.Config.ARGB_8888
            val enhancedBitmap = originalBitmap.copy(config, false)

            // Apply a gentle blur to smooth out harsh details and create more cohesive colors
            val blurredBitmap = applyGaussianBlur(enhancedBitmap, 3f)

            // Enhance color saturation for more vibrant palette extraction
            enhanceSaturation(blurredBitmap, 1.2f)
        } catch (e: Exception) {
            Timber.w(e, "Failed to enhance bitmap, using original")
            originalBitmap
        }
    }

    /**
     * Apply Gaussian blur using a modern, efficient algorithm
     */
    private fun applyGaussianBlur(bitmap: Bitmap, radius: Float): Bitmap {
        if (radius <= 0) return bitmap

        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val blurred = bitmap.copy(config, true)

        val width = blurred.width
        val height = blurred.height
        val pixels = IntArray(width * height)
        blurred.getPixels(pixels, 0, width, 0, 0, width, height)

        // Apply horizontal blur
        blurPixels(pixels, width, height, radius.toInt(), true)
        // Apply vertical blur
        blurPixels(pixels, width, height, radius.toInt(), false)

        blurred.setPixels(pixels, 0, width, 0, 0, width, height)
        return blurred
    }

    /**
     * Efficient box blur implementation for horizontal and vertical passes
     */
    private fun blurPixels(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val blur = IntArray(pixels.size)
        val kernel = createGaussianKernel(radius)
        val kernelSize = kernel.size
        val kernelRadius = kernelSize / 2

        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                var a = 0f

                for (k in 0 until kernelSize) {
                    val weight = kernel[k]
                    val sampleX = if (horizontal) {
                        (x + k - kernelRadius).coerceIn(0, width - 1)
                    } else x
                    val sampleY = if (horizontal) y else {
                        (y + k - kernelRadius).coerceIn(0, height - 1)
                    }

                    val pixel = pixels[sampleY * width + sampleX]
                    r += ((pixel shr 16) and 0xFF) * weight
                    g += ((pixel shr 8) and 0xFF) * weight
                    b += (pixel and 0xFF) * weight
                    a += ((pixel shr 24) and 0xFF) * weight
                }

                val blurredPixel = (a.toInt() shl 24) or
                        (r.toInt() shl 16) or
                        (g.toInt() shl 8) or
                        b.toInt()
                blur[y * width + x] = blurredPixel
            }
        }

        System.arraycopy(blur, 0, pixels, 0, pixels.size)
    }

    /**
     * Create a Gaussian kernel for blur
     */
    private fun createGaussianKernel(radius: Int): FloatArray {
        val size = radius * 2 + 1
        val kernel = FloatArray(size)
        val sigma = radius / 3f
        var sum = 0f

        for (i in kernel.indices) {
            val x = i - radius
            kernel[i] = kotlin.math.exp(-(x * x) / (2 * sigma * sigma))
            sum += kernel[i]
        }

        // Normalize
        for (i in kernel.indices) {
            kernel[i] /= sum
        }

        return kernel
    }

    /**
     * Enhance color saturation for more vibrant palette extraction
     */
    private fun enhanceSaturation(bitmap: Bitmap, factor: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val hsv = FloatArray(3)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f) // Enhance saturation

            val enhancedColor = android.graphics.Color.HSVToColor(
                (pixel shr 24) and 0xFF, hsv
            )
            pixels[i] = enhancedColor
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Determines whether light or dark text is more readable on a given background color.
     */
    @ColorInt
    private fun getBestTextColorForBackground(@ColorInt backgroundColor: Int, @ColorInt lightColor: Int, @ColorInt darkColor: Int): Int {
        val contrastWithLight = ColorUtils.calculateContrast(lightColor, backgroundColor)
        val contrastWithDark = ColorUtils.calculateContrast(darkColor, backgroundColor)
        return if (contrastWithLight > contrastWithDark) lightColor else darkColor
    }
}