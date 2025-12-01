// File: java\com\example\holodex\data\repository\YouTubeStreamRepository.kt
package com.example.holodex.data.repository

import android.content.SharedPreferences
import com.example.holodex.data.AppPreferenceConstants
import com.example.holodex.data.model.AudioStreamDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
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

    suspend fun getAudioStreamDetails(videoId: String, preferM4a: Boolean = false): Result<AudioStreamDetails> {
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

                // 1. Determine which pool of streams to choose from
                val candidateStreams = if (preferM4a) {
                    val m4aStreams = allAudioStreams.filter { it.format == MediaFormat.M4A }
                    // Fallback to all streams if no M4A is found, to prevent crash
                    if (m4aStreams.isNotEmpty()) m4aStreams else allAudioStreams
                } else {
                    allAudioStreams
                }

                val audioQualityPref = sharedPreferences.getString(
                    AppPreferenceConstants.PREF_AUDIO_QUALITY,
                    AppPreferenceConstants.AUDIO_QUALITY_BEST
                ) ?: AppPreferenceConstants.AUDIO_QUALITY_BEST

                // Helper to apply quality/bitrate filters
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

                // 2. Apply your specific logic to the candidate list
                // We run your exact logic on 'candidateStreams'
                val bestAudioStream =
                    // Priority 1: Original Japanese track
                    applyQualityFilterAndSort(candidateStreams.filter {
                        it.audioTrackType == AudioTrackType.ORIGINAL && it.audioLocale?.language == Locale.JAPANESE.language
                    })
                    // Priority 2: Any Original track
                        ?: applyQualityFilterAndSort(candidateStreams.filter {
                            it.audioTrackType == AudioTrackType.ORIGINAL
                        })
                        // Priority 3: Any Japanese track
                        ?: applyQualityFilterAndSort(candidateStreams.filter {
                            it.audioLocale?.language == Locale.JAPANESE.language
                        })
                        // Final Fallback: The best of whatever is left
                        ?: applyQualityFilterAndSort(candidateStreams)


                if (bestAudioStream != null) {
                    val finalUrl = bestAudioStream.content
                        ?: return@withContext Result.failure(Exception("Selected best audio stream has no URL for $videoId`."))
                    val finalFormat = bestAudioStream.format?.getName()?.uppercase() ?: "UNKNOWN"
                    val qualityDesc = "${bestAudioStream.averageBitrate}kbps"

                    Timber.i("$TAG: Resolved stream for $videoId. PreferM4a: $preferM4a. Result: $finalFormat $qualityDesc")

                    return@withContext Result.success(
                        AudioStreamDetails(
                            streamUrl = finalUrl,
                            format = finalFormat,
                            quality = qualityDesc
                        )
                    )
                } else {
                    return@withContext Result.failure(Exception("No suitable audio stream found."))
                }

            } catch (e: Exception) {
                Timber.e(e, "$TAG: Unexpected error for $videoId")
                return@withContext Result.failure(e)
            }
        }
    }
}