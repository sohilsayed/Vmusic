package com.example.holodex.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.holodex.playback.image.CoilBitmapLoader
import com.example.holodex.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "NotificationDebug"
private const val DEFAULT_CHANNEL_ID = "default_channel_id"

@UnstableApi
@AndroidEntryPoint
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @Inject lateinit var player: Player
    @Inject lateinit var coilBitmapLoader: CoilBitmapLoader
    @Inject lateinit var playbackDao: com.example.holodex.playback.data.model.PlaybackDao

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).i("onCreate: Service creating...")

        createNotificationChannel()
        initializeMediaSession()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(DEFAULT_CHANNEL_ID)
            .build()

        setMediaNotificationProvider(notificationProvider)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(DEFAULT_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                DEFAULT_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(getSingleTopActivityPendingIntent())
            .setId("holodex_music_media_session")
            .setBitmapLoader(coilBitmapLoader)
            .build()

        addSession(mediaSession!!)
    }

    private fun getSingleTopActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {

            if (!player.playWhenReady || player.playbackState == Player.STATE_IDLE) {
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
        Timber.tag(TAG).i("onDestroy: Service destroyed.")
    }
}