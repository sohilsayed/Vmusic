package com.example.holodex.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.navigation.compose.rememberNavController
import com.example.holodex.MyApp
import com.example.holodex.R
import com.example.holodex.playback.PlaybackRequestManager
import com.example.holodex.playback.domain.model.PlaybackItem
import com.example.holodex.service.ARG_PLAYBACK_ITEMS_LIST
import com.example.holodex.service.ARG_SHOULD_SHUFFLE
import com.example.holodex.service.ARG_START_INDEX
import com.example.holodex.service.ARG_START_POSITION_SEC
import com.example.holodex.service.CUSTOM_COMMAND_PREPARE_FROM_REQUEST
import com.example.holodex.service.MediaPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playbackRequestManager: PlaybackRequestManager

    @Inject
    lateinit var player: Player

    private val myApp: MyApp by lazy { application as MyApp }
    private var mediaController: MediaController? = null
    private lateinit var sessionToken: SessionToken

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Timber.d("Permission [${it.key}] granted: ${it.value}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSplashScreen()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }

        sessionToken = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
        checkAndRequestPermissions()

        setContent {
            val navController = rememberNavController()
            // Clean entry point using the Scaffold
            MainScreenScaffold(
                navController = navController,
                playbackRequestManager = playbackRequestManager,
                activity = this,
                player = player
            )
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onStart() {
        super.onStart()
        connectMediaController()
        Timber.d("MainActivity onStart: Triggering download reconciliation.")
        myApp.reconcileCompletedDownloads()
    }

    override fun onStop() {
        super.onStop()
        Timber.tag(TAG).d("MainActivity onStop - keeping MediaController connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaController?.release()
        mediaController = null
        Timber.tag(TAG).d("MediaController released in onDestroy")
    }

    // Exposed to MainScreenScaffold
    internal fun sendPlaybackRequestToService(
        items: List<PlaybackItem>,
        startIndex: Int,
        startPositionSec: Long,
        shouldShuffle: Boolean = false
    ) {
        if (items.isEmpty()) {
            Timber.tag(TAG).w("sendPlaybackRequestToService called with empty item list. Aborting."); return
        }
        val serviceIntent = Intent(this, MediaPlaybackService::class.java)

        try {
            items.forEach { item ->
                if (item.streamUri?.startsWith("content://") == true) {
                    val uri = item.streamUri!!.toUri()
                    grantUriPermission("com.example.holodex", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to grant URI permissions.")
        }

        startService(serviceIntent)

        if (mediaController == null || !mediaController!!.isConnected) {
            Timber.tag(TAG).w("MediaController not available/connected. Attempting to connect then send.")
            connectMediaController { success ->
                if (success) {
                    sendPlaybackRequestToService(items, startIndex, startPositionSec, shouldShuffle)
                } else {
                    Toast.makeText(this, getString(R.string.error_player_service_not_ready), Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        val commandArgs = Bundle().apply {
            putParcelableArrayList(ARG_PLAYBACK_ITEMS_LIST, ArrayList(items))
            putInt(ARG_START_INDEX, startIndex)
            putLong(ARG_START_POSITION_SEC, startPositionSec)
            putBoolean(ARG_SHOULD_SHUFFLE, shouldShuffle)
        }
        val command = SessionCommand(CUSTOM_COMMAND_PREPARE_FROM_REQUEST, Bundle.EMPTY)
        val resultFuture: ListenableFuture<SessionResult> = mediaController!!.sendCustomCommand(command, commandArgs)

        resultFuture.addListener({
            try {
                val result: SessionResult = resultFuture.get()
                if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                    Timber.tag(TAG).w("Custom command failed: ${result.resultCode}")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error processing result of playback command")
            }
        }, MoreExecutors.directExecutor())
    }

    private fun connectMediaController(onConnected: ((Boolean) -> Unit)? = null) {
        if (mediaController?.isConnected == true) {
            onConnected?.invoke(true)
            return
        }
        if (mediaController != null) {
            try { mediaController?.release() } catch (e: Exception) { }
            mediaController = null
        }
        val serviceIntent = Intent(this, MediaPlaybackService::class.java)
        startService(serviceIntent)

        val controllerFuture: ListenableFuture<MediaController> = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                onConnected?.invoke(true)
            } catch (e: Exception) {
                mediaController = null
                Timber.tag(TAG).e(e, "Error connecting MediaController")
                onConnected?.invoke(false)
            }
        }, MoreExecutors.directExecutor())
    }
}