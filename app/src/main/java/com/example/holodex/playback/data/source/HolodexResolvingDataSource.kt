package com.example.holodex.playback.data.source

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.example.holodex.data.repository.YouTubeStreamRepository
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/**
 * Intercepts "holodex://" URIs.
 * Resolves the actual audio stream URL (m4a/webm) just-in-time.
 * This allows the UI to set the queue instantly without waiting for network calls.
 */
@UnstableApi
class HolodexResolvingDataSource @Inject constructor(
    private val streamRepository: YouTubeStreamRepository
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri

        // We only intercept our custom scheme
        if (uri.scheme == "holodex" && uri.host == "resolve") {
            val videoId = uri.lastPathSegment ?: return dataSpec

            Timber.d("ResolvingDataSource: JIT Resolving for $videoId")

            // runBlocking is necessary here because this API is synchronous.
            // It runs on ExoPlayer's background loading thread, so it DOES NOT block the UI.
            val streamDetails = try {
                runBlocking {
                    streamRepository.getAudioStreamDetails(videoId).getOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve stream for $videoId")
                null
            }

            if (streamDetails != null) {
                Timber.i("ResolvingDataSource: Resolved $videoId -> ${streamDetails.streamUrl}")
                return dataSpec.buildUpon()
                    .setUri(Uri.parse(streamDetails.streamUrl))
                    .build()
            } else {
                Timber.e("ResolvingDataSource: Failed to resolve URL for $videoId")
                // We throw here to trigger the Player's error handling state
                throw java.io.IOException("Could not resolve stream for video $videoId")
            }
        }

        // Pass through local files and standard HTTP urls
        return dataSpec
    }
}