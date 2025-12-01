package com.example.holodex.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.navigation.compose.rememberNavController
import com.example.holodex.R
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


    @Inject lateinit var player: ExoPlayer

    private var mediaController: MediaController? = null
    private lateinit var sessionToken: SessionToken

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

        sessionToken = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
        checkAndRequestPermissions()

        setContent {
            val navController = rememberNavController()
            MainScreenScaffold(
                navController = navController,
                activity = this,
                player = player // Pass it here
            )
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onStart() {
        super.onStart()

    }

    override fun onStop() {
        super.onStop()

    }

    internal fun sendPlaybackRequestToService(
        items: List<PlaybackItem>,
        startIndex: Int,
        startPositionSec: Long,
        shouldShuffle: Boolean = false
    ) {
        if (items.isEmpty()) return

        val controller = mediaController ?: run {
            Toast.makeText(
                this,
                getString(R.string.error_player_service_not_ready),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val commandArgs = Bundle().apply {
            putParcelableArrayList(ARG_PLAYBACK_ITEMS_LIST, ArrayList(items))
            putInt(ARG_START_INDEX, startIndex)
            putLong(ARG_START_POSITION_SEC, startPositionSec)
            putBoolean(ARG_SHOULD_SHUFFLE, shouldShuffle)
        }
        val command = SessionCommand(CUSTOM_COMMAND_PREPARE_FROM_REQUEST, Bundle.EMPTY)
        val resultFuture: ListenableFuture<SessionResult> =
            controller.sendCustomCommand(command, commandArgs)

        resultFuture.addListener({
            try {
                val result = resultFuture.get()
                if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                    Timber.w("Custom playback command failed: ${result.resultCode}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing playback command result")
            }
        }, MoreExecutors.directExecutor())
    }
}