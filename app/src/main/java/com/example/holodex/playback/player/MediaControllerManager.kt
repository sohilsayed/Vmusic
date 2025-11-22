// File: java/com/example/holodex/playback/player/MediaControllerManager.kt (NEW FILE)
package com.example.holodex.playback.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.holodex.service.MediaPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Singleton manager that provides a single, app-wide instance of MediaController.
 * It handles the asynchronous connection to the MediaSessionService.
 */
@Singleton
class MediaControllerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(context, sessionToken).buildAsync()

    private val deferredController = CompletableDeferred<MediaController>()

    init {
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                deferredController.complete(controller)
                Timber.d("MediaControllerManager: MediaController connected successfully.")
            } catch (e: Exception) {
                deferredController.completeExceptionally(e)
                Timber.e(e, "MediaControllerManager: Failed to connect MediaController.")
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Suspends until the MediaController is connected, then returns the instance.
     */
    suspend fun awaitController(): MediaController {
        return deferredController.await()
    }

    fun release() {
        if (controllerFuture.isDone) {
            MediaController.releaseFuture(controllerFuture)
        }
    }
}