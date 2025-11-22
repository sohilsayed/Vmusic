// File: java/com/example/holodex/service/MediaPlaybackService.kt
package com.example.holodex.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.ui.PlayerNotificationManager
import coil.imageLoader
import coil.request.ImageRequest
import com.example.holodex.R
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.playback.domain.usecase.PlayItemsUseCase
import com.example.holodex.playback.util.playbackStateToString
import com.example.holodex.ui.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import timber.log.Timber
import javax.inject.Inject

// --- Utility function for Bitmap conversion ---
fun Drawable?.toBitmapSafe(): Bitmap {
    if (this == null) {
        Timber.tag("BmpSafe").w("Drawable was null, returning 1x1 bitmap.")
        return createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
    if (this is BitmapDrawable && this.bitmap != null) { return this.bitmap }
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    this.setBounds(0, 0, canvas.width, canvas.height)
    this.draw(canvas)
    return bitmap
}

// --- Constants ---
const val CUSTOM_COMMAND_PREPARE_FROM_REQUEST = "com.example.holodex.PREPARE_FROM_REQUEST"
const val ARG_PLAYBACK_ITEMS_LIST = "playback_items_list"
const val ARG_START_INDEX = "start_index"
const val ARG_START_POSITION_SEC = "start_position_sec"
const val ARG_SHOULD_SHUFFLE = "should_shuffle_playlist"

private const val SERVICE_NOTIFICATION_ID = 123
private const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "holodex_playback_channel_v3"
private const val SERVICE_TAG = "MediaPlaybackService"

@UnstableApi
@AndroidEntryPoint
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    @Inject lateinit var player: Player
    @Inject lateinit var playItemsUseCase: PlayItemsUseCase

    private lateinit var notificationManager: PlayerNotificationManager
    private var defaultNotificationBitmap: Bitmap? = null
    private var isServiceInForeground = false

    private val playerListener = PlayerStateListener()

    override fun onCreate() {
        super.onCreate()
        Timber.tag(SERVICE_TAG).i("onCreate: Service creating...")

        defaultNotificationBitmap = (ContextCompat.getDrawable(this, R.drawable.ic_default_album_art_placeholder)
            ?.toBitmapSafe()) ?: createBitmap(64, 64, Bitmap.Config.ARGB_8888)

        player.addListener(playerListener)

        initializeMediaSession()
        initializeNotificationManager()
        Timber.tag(SERVICE_TAG).i("onCreate: Service creation complete.")
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(getSingleTopActivityPendingIntent())
            .setCallback(MediaSessionCallback())
            .setId("holodex_music_media_session_${System.currentTimeMillis()}")
            .build()

        addSession(mediaSession!!)
    }

    private fun initializeNotificationManager() {
        val mediaDescriptionAdapter = ServiceMediaDescriptionAdapter()
        val notificationListener = ServiceNotificationListener()

        notificationManager = PlayerNotificationManager.Builder(
            this,
            SERVICE_NOTIFICATION_ID,
            PLAYBACK_NOTIFICATION_CHANNEL_ID
        )
            .setChannelNameResourceId(R.string.playback_notification_channel_name)
            .setChannelDescriptionResourceId(R.string.playback_notification_channel_description)
            .setMediaDescriptionAdapter(mediaDescriptionAdapter)
            .setNotificationListener(notificationListener)
            .setSmallIconResourceId(R.drawable.ic_stat_music_note)
            .build().apply {
                setUseRewindAction(false)
                setUseFastForwardAction(false)
                setUseNextAction(true)
                setUsePreviousAction(true)
                setColorized(true)
                setUseNextActionInCompactView(true)
                setUsePreviousActionInCompactView(true)
                setPlayer(player)
            }
    }

    private fun getSingleTopActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Deprecated but still called. Handled by PlayerNotificationManager.
    }

    private inner class PlayerStateListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Timber.tag(SERVICE_TAG).i("PlayerListener.onPlaybackStateChanged: %s", playbackStateToString(playbackState))
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Timber.tag(SERVICE_TAG).i("PlayerListener.onIsPlayingChanged: %b", isPlaying)
        }
    }

    private inner class ServiceMediaDescriptionAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence {
            return player.currentMediaItem?.mediaMetadata?.title ?: getString(R.string.unknown_title)
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            return getSingleTopActivityPendingIntent()
        }

        override fun getCurrentContentText(player: Player): CharSequence? {
            return player.currentMediaItem?.mediaMetadata?.artist
        }

        override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): Bitmap? {
            val artworkUri = player.currentMediaItem?.mediaMetadata?.artworkUri
            if (artworkUri != null) {
                val request = ImageRequest.Builder(applicationContext)
                    .data(artworkUri)
                    .allowHardware(false)
                    .target(
                        onSuccess = { drawable -> callback.onBitmap(drawable.toBitmapSafe()) },
                        onError = { defaultNotificationBitmap?.let { callback.onBitmap(it) } }
                    ).build()
                applicationContext.imageLoader.enqueue(request)
                return defaultNotificationBitmap
            }
            return defaultNotificationBitmap
        }
    }

    private inner class ServiceNotificationListener : PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
            if (ongoing) {
                if (!isServiceInForeground) {
                    try {
                        startForeground(notificationId, notification)
                        isServiceInForeground = true
                    } catch (e: Exception) {
                        Timber.e(e, "CRITICAL EXCEPTION during startForeground().")
                    }
                }
            } else {
                if (isServiceInForeground) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isServiceInForeground = false
                }
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            if (dismissedByUser) {
                player.stop()
                stopSelf()
            }
            isServiceInForeground = false
        }
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession = mediaSession!!

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = SessionCommands.Builder()
                .add(SessionCommand(CUSTOM_COMMAND_PREPARE_FROM_REQUEST, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(sessionCommands, player.availableCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CUSTOM_COMMAND_PREPARE_FROM_REQUEST) {
                val itemsParcelable: ArrayList<PlaybackItem>? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        args.getParcelableArrayList(ARG_PLAYBACK_ITEMS_LIST, PlaybackItem::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        args.getParcelableArrayList(ARG_PLAYBACK_ITEMS_LIST)
                    }
                val startIndex = args.getInt(ARG_START_INDEX, 0)
                val startPositionSec = args.getLong(ARG_START_POSITION_SEC, 0L)
                val shouldShufflePlaylist = args.getBoolean(ARG_SHOULD_SHUFFLE, false)

                if (itemsParcelable.isNullOrEmpty()) {
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                }

                return serviceScope.future {
                    try {
                        playItemsUseCase(itemsParcelable, startIndex, startPositionSec * 1000L, shouldShufflePlaylist)
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    } catch (e: Exception) {
                        Timber.e(e, "Error executing playItemsUseCase from custom command.")
                        SessionResult(SessionError.ERROR_UNKNOWN)
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady && player.playbackState != Player.STATE_BUFFERING) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        player.removeListener(playerListener)
        if (::notificationManager.isInitialized) {
            notificationManager.setPlayer(null)
        }
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }
}