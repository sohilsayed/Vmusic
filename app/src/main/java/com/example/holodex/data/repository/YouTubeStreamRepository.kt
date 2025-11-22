// File: java\com\example\holodex\data\repository\YouTubeStreamRepository.kt
package com.example.holodex.data.repository

import android.content.SharedPreferences
import com.example.holodex.data.model.AudioStreamDetails
import com.example.holodex.viewmodel.AppPreferenceConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeStreamRepository @Inject constructor(
    private val sharedPreferences: SharedPreferences,
) {

    companion object {
        private const val TAG = "YouTubeStreamRepo"
    }

    private val saverMaxBitrate = 96
    private val standardMaxBitrate = 140

    suspend fun getAudioStreamDetails(videoId: String): Result<AudioStreamDetails> {
        return withContext(Dispatchers.IO) {
            try {
                val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
                val ytService = NewPipe.getService(ServiceList.YouTube.serviceId)
                    ?: return@withContext Result.failure(Exception("YouTube service not found."))

                val streamInfo: StreamInfo = StreamInfo.getInfo(ytService, youtubeUrl)

                val allAudioStreams: List<AudioStream> = streamInfo.audioStreams

                if (allAudioStreams.isEmpty()) {
                    return@withContext Result.failure(Exception("No audio streams found for video ID `$videoId`."))
                }

                val audioQualityPref = sharedPreferences.getString(
                    AppPreferenceConstants.PREF_AUDIO_QUALITY,
                    AppPreferenceConstants.AUDIO_QUALITY_BEST
                ) ?: AppPreferenceConstants.AUDIO_QUALITY_BEST

                val applyQualityFilterAndSort: (List<AudioStream>) -> AudioStream? = { streams ->
                    val qualityFiltered = when (audioQualityPref) {
                        AppPreferenceConstants.AUDIO_QUALITY_SAVER ->
                            streams.filter { it.averageBitrate in 1..saverMaxBitrate }
                                .ifEmpty { streams.filter { it.averageBitrate in 1..standardMaxBitrate } }
                                .ifEmpty { streams }
                        AppPreferenceConstants.AUDIO_QUALITY_STANDARD ->
                            streams.filter { it.averageBitrate in 1..standardMaxBitrate }
                                .ifEmpty { streams }
                        else -> streams
                    }
                    qualityFiltered.maxByOrNull { it.averageBitrate }
                }

                // --- FIX: Expanded fallback to include OPUS and WEBM with priority ---
                val bestAudioStream =
                    // Priority 1: Original Japanese track
                    applyQualityFilterAndSort(allAudioStreams.filter {
                        it.audioTrackType == AudioTrackType.ORIGINAL && it.audioLocale?.language == Locale.JAPANESE.language
                    })
                    // Priority 2: Any Original track
                        ?: applyQualityFilterAndSort(allAudioStreams.filter {
                            it.audioTrackType == AudioTrackType.ORIGINAL
                        })
                        // Priority 3: Any Japanese track
                        ?: applyQualityFilterAndSort(allAudioStreams.filter {
                            it.audioLocale?.language == Locale.JAPANESE.language
                        })
                        // Final Fallback: The best of whatever is left
                        ?: applyQualityFilterAndSort(allAudioStreams)


                if (bestAudioStream != null) {
                    val finalUrl = bestAudioStream.content
                        ?: return@withContext Result.failure(Exception("Selected best audio stream has no URL for $videoId`."))
                    val finalFormat = bestAudioStream.format?.getName()?.uppercase() ?: "UNKNOWN"
                    val qualityDesc = "${bestAudioStream.averageBitrate}kbps"
                    val trackType = bestAudioStream.audioTrackType?.name ?: "UNKNOWN_TYPE"
                    Timber.i("$TAG: Resolved stream for $videoId. Quality: '$audioQualityPref', Selected: $finalFormat $qualityDesc, Track Type: $trackType, URL Length: ${finalUrl.length}")
                    return@withContext Result.success(
                        AudioStreamDetails(
                            streamUrl = finalUrl,
                            format = finalFormat,
                            quality = qualityDesc
                        )
                    )
                } else {
                    Timber.e("$TAG: Could not select a best audio stream for $videoId after all fallbacks.")
                    return@withContext Result.failure(Exception("No suitable audio stream found for $videoId after all fallbacks."))
                }

            } catch (e: Exception) {
                Timber.e(e, "$TAG: Unexpected error for $videoId")
                return@withContext Result.failure(
                    Exception("An unexpected error occurred while fetching stream for $videoId: ${e.message}", e)
                )
            }
        }
    }
}