package com.example.holodex.playback.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.holodex.service.MediaPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.guava.await
import timber.log.Timber
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaControllerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))

    // We hold the future so we can check/reset it
    private var controllerFuture: ListenableFuture<MediaController>? = null

    /**
     * Ensures the Service is started and the Controller is connected.
     * FAST CHECK: If already connected, returns immediately.
     * SLOW CHECK: If dead, triggers restart.
     */
    suspend fun ensureServiceConnection(): MediaController {
        // 1. FAST PATH: Check in-memory instance
        val cachedController = synchronized(this) {
            val f = controllerFuture
            // If future exists, is done, and not cancelled, check the result
            if (f != null && f.isDone && !f.isCancelled) {
                try {
                    f.get() // Non-blocking because isDone is true
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        // If alive and connected, return immediately. No overhead.
        if (cachedController != null && cachedController.isConnected) {
            return cachedController
        }

        // 2. SLOW PATH: Reconnect/Rebuild
        Timber.d("MediaControllerManager: Controller not connected. Reconnecting...")
        return connectOrGet()
    }

    // Helper to get or rebuild the controller
    private suspend fun connectOrGet(): MediaController {
        val currentFuture = synchronized(this) {
            if (controllerFuture == null || controllerFuture!!.isCancelled) {
                Timber.i("MediaControllerManager: Building new controller connection...")
                controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            }
            controllerFuture!!
        }

        return try {
            val controller = currentFuture.await()

            // Critical Check: If the controller exists but is disconnected (Service died),
            // we must release it and rebuild.
            if (!controller.isConnected) {
                Timber.w("MediaControllerManager: Controller found but DISCONNECTED. Rebuilding...")
                release() // Clear old one
                return connectOrGet() // Recursively try again (will hit the null check above)
            }

            controller
        } catch (e: Exception) {
            Timber.e(e, "MediaControllerManager: Failed to connect.")
            if (e is ExecutionException || e is CancellationException) {
                // If the future failed, clear it so next try rebuilds
                synchronized(this) { controllerFuture = null }
            }
            throw e
        }
    }

    suspend fun awaitController(): MediaController {
        return ensureServiceConnection()
    }

    fun release() {
        synchronized(this) {
            controllerFuture?.let {
                if (it.isDone && !it.isCancelled) {
                    try {
                        MediaController.releaseFuture(it)
                    } catch (e: Exception) {
                        Timber.e("Error releasing controller future")
                    }
                }
            }
            controllerFuture = null
        }
    }
}