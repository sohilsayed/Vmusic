// File: java/com/example/holodex/playback/data/preload/PreloadStatusController.kt
package com.example.holodex.playback.data.preload

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import timber.log.Timber

@UnstableApi
class PreloadStatusController(
    private val getCurrentIndex: () -> Int,
    private val preloadDurationMs: Long = 10_000L
) : TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {

    companion object {
        private const val TAG = "PreloadStatusController"
    }

    override fun getTargetPreloadStatus(
        rankingData: Int
    ): DefaultPreloadManager.PreloadStatus {
        val currentIndex = getCurrentIndex()
        val ranking = rankingData - currentIndex

        return when (ranking) {
            1 -> {
                Timber.i("$TAG: Preloading next item (index $rankingData) for ${preloadDurationMs}ms")
                DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(preloadDurationMs)
            }
            2 -> {
                Timber.i("$TAG: Preloading second item (index $rankingData) for ${preloadDurationMs / 2}ms")
                DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(preloadDurationMs / 2)
            }
            else -> {
                DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
            }
        }
    }
}