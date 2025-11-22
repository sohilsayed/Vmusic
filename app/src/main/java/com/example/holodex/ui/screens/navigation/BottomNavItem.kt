// --- FULL REPLACEMENT of the file content ---
package com.example.holodex.ui.screens.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.holodex.R
import com.example.holodex.ui.navigation.AppDestinations

sealed class BottomNavItem(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector
) {
    object Discover : BottomNavItem(
        route = AppDestinations.DISCOVERY_ROUTE,
        titleResId = R.string.bottom_nav_discover, // Add string
        icon = Icons.Filled.Explore
    )

    object Browse : BottomNavItem(
        route = AppDestinations.HOME_ROUTE,
        titleResId = R.string.bottom_nav_browse,
        icon = Icons.Filled.Search
    )

    object Library : BottomNavItem(
        route = AppDestinations.LIBRARY_ROUTE,
        titleResId = R.string.bottom_nav_library,
        icon = Icons.Filled.LibraryMusic
    )

    object Downloads : BottomNavItem(
        route = AppDestinations.DOWNLOADS_ROUTE,
        titleResId = R.string.bottom_nav_downloads,
        icon = Icons.Filled.Download
    )
}