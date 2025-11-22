package com.example.holodex.ui.navigation

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.holodex.auth.LoginScreen
import com.example.holodex.ui.screens.ChannelScreen
import com.example.holodex.ui.screens.DiscoveryScreen
import com.example.holodex.ui.screens.DownloadsScreen
import com.example.holodex.ui.screens.ExternalChannelScreen
import com.example.holodex.ui.screens.ForYouScreen
import com.example.holodex.ui.screens.FullListViewScreen
import com.example.holodex.ui.screens.HomeScreen
import com.example.holodex.ui.screens.LibraryScreen
import com.example.holodex.ui.screens.PlaylistDetailsScreen
import com.example.holodex.ui.screens.SettingsScreen
import com.example.holodex.ui.screens.VideoDetailsScreen
import com.example.holodex.viewmodel.ChannelDetailsViewModel
import com.example.holodex.viewmodel.FullListViewModel
import com.example.holodex.viewmodel.MusicCategoryType
import com.example.holodex.viewmodel.PlaylistDetailsViewModel
import com.example.holodex.viewmodel.PlaylistManagementViewModel
import com.example.holodex.viewmodel.SettingsViewModel
import com.example.holodex.viewmodel.VideoDetailsViewModel
import com.example.holodex.viewmodel.VideoListViewModel
import org.orbitmvi.orbit.compose.collectAsState // <--- Import

@SuppressLint("UnstableApi")
@Composable
fun HolodexNavHost(
    navController: NavHostController,
    videoListViewModel: VideoListViewModel,
    playlistManagementViewModel: PlaylistManagementViewModel,
    activity: ComponentActivity,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.LIBRARY_ROUTE,
        modifier = modifier.fillMaxSize()
    ) {
        // ... (Other composables unchanged: Discovery, ForYou, Library, Downloads)
        composable(AppDestinations.DISCOVERY_ROUTE) {
            DiscoveryScreen(navController = navController)
        }

        composable(AppDestinations.FOR_YOU_ROUTE) {
            ForYouScreen(navController = navController)
        }

        composable(AppDestinations.HOME_ROUTE) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()

            // FIX: Collect state from Orbit
            val state by settingsViewModel.collectAsState()
            val currentApiKey = state.currentApiKey

            if (currentApiKey.isBlank()) {
                ApiKeyMissingContent(navController = navController)
            } else {
                HomeScreen(
                    navController = navController,
                    videoListViewModel = videoListViewModel,
                    playlistManagementViewModel = playlistManagementViewModel
                )
            }
        }

        composable(AppDestinations.LIBRARY_ROUTE) {
            LibraryScreen(navController = navController, playlistManagementViewModel = playlistManagementViewModel)
        }

        composable(AppDestinations.DOWNLOADS_ROUTE) {
            DownloadsScreen(navController = navController, playlistManagementViewModel = playlistManagementViewModel)
        }

        composable(AppDestinations.SETTINGS_ROUTE) {
            val vListVm: VideoListViewModel = hiltViewModel(activity)
            SettingsScreen(
                navController = navController,
                onNavigateUp = { navController.popBackStack() },
                onApiKeySavedRestartNeeded = { vListVm.refreshCurrentListViaPull() }
            )
        }

        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(onLoginSuccess = { navController.popBackStack() })
        }

        // ... (Parameterized composables unchanged)
        composable(
            route = "external_channel_details/{${ChannelDetailsViewModel.CHANNEL_ID_ARG}}",
            arguments = listOf(navArgument(ChannelDetailsViewModel.CHANNEL_ID_ARG) { type = NavType.StringType })
        ) {
            ExternalChannelScreen(navController = navController, onNavigateUp = { navController.popBackStack() })
        }

        composable(
            route = "channel_details/{${ChannelDetailsViewModel.CHANNEL_ID_ARG}}",
            arguments = listOf(navArgument(ChannelDetailsViewModel.CHANNEL_ID_ARG) { type = NavType.StringType })
        ) {
            ChannelScreen(navController = navController, onNavigateUp = { navController.popBackStack() })
        }

        composable(
            route = AppDestinations.FULL_LIST_VIEW_ROUTE_TEMPLATE,
            arguments = listOf(
                navArgument(FullListViewModel.CATEGORY_TYPE_ARG) { type = NavType.StringType },
                navArgument(FullListViewModel.ORG_ARG) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val categoryName = backStackEntry.arguments?.getString(FullListViewModel.CATEGORY_TYPE_ARG) ?: MusicCategoryType.TRENDING.name
            val category = try {
                MusicCategoryType.valueOf(categoryName)
            } catch (e: IllegalArgumentException) {
                MusicCategoryType.TRENDING
            }
            FullListViewScreen(navController = navController, categoryType = category)
        }

        composable(
            AppDestinations.PLAYLIST_DETAILS_ROUTE_TEMPLATE,
            arguments = listOf(navArgument(PlaylistDetailsViewModel.PLAYLIST_ID_ARG) { type = NavType.StringType })
        ) {
            PlaylistDetailsScreen(
                navController = navController,
                playlistManagementViewModel = playlistManagementViewModel,
                onNavigateUp = { navController.popBackStack() }
            )
        }

        composable(
            AppDestinations.VIDEO_DETAILS_ROUTE_TEMPLATE,
            arguments = listOf(navArgument(VideoDetailsViewModel.VIDEO_ID_ARG) { type = NavType.StringType })
        ) {
            VideoDetailsScreen(navController = navController, onNavigateUp = { navController.popBackStack() })
        }
    }
}

@Composable
private fun ApiKeyMissingContent(navController: NavHostController) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("API Key Required", style = MaterialTheme.typography.headlineSmall)
            Button(
                onClick = { navController.navigate(AppDestinations.SETTINGS_ROUTE) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Go to Settings")
            }
        }
    }
}