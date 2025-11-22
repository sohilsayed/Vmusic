// File: java/com/example/holodex/ui/screens/SettingsScreen.kt
package com.example.holodex.ui.screens


import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.BuildConfig
import com.example.holodex.R
import com.example.holodex.auth.AuthState
import com.example.holodex.auth.AuthViewModel
import com.example.holodex.ui.composables.ApiKeyInputScreen
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.viewmodel.AppPreferenceConstants
import com.example.holodex.viewmodel.ScanStatus
import com.example.holodex.viewmodel.SettingsViewModel
import com.example.holodex.viewmodel.ThemePreference
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun SettingsScreen(
    navController: NavController,
    onNavigateUp: () -> Unit,
    onApiKeySavedRestartNeeded: () -> Unit
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val scanStatus by settingsViewModel.scanStatus.collectAsStateWithLifecycle()

    // --- START: Permission and Scan Launcher Logic ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(context, "Permission granted. Starting scan...", Toast.LENGTH_SHORT).show()
            settingsViewModel.runLegacyFileScan()
        } else {
            Toast.makeText(
                context,
                "Storage permission is required to find old downloads.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val cacheClearStatus by settingsViewModel.cacheClearStatus.collectAsState()
    var isClearingCache by remember { mutableStateOf(false) }
    val currentThemePref by settingsViewModel.currentThemePreference.collectAsStateWithLifecycle()

    val currentImageQuality by settingsViewModel.currentImageQuality.collectAsStateWithLifecycle()
    val currentAudioQuality by settingsViewModel.currentAudioQuality.collectAsStateWithLifecycle()
    val currentListLoadingConfig by settingsViewModel.currentListLoadingConfig.collectAsStateWithLifecycle() // Renamed from DataLoadingIntensity
    val currentBufferingStrategy by settingsViewModel.currentBufferingStrategy.collectAsStateWithLifecycle()
    val autoplayNextVideoEnabled by settingsViewModel.autoplayNextVideoEnabled.collectAsStateWithLifecycle() // NEW: Autoplay preference state
    val shuffleOnPlayStartEnabled by settingsViewModel.shuffleOnPlayStartEnabled.collectAsStateWithLifecycle()
    val downloadLocation by settingsViewModel.downloadLocation.collectAsStateWithLifecycle()
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                Timber.d("Folder selected: $uri")
                settingsViewModel.saveDownloadLocation(uri)
            } else {
                Timber.d("Folder selection cancelled by user.")
            }
        }
    )
    var showRestartMessageForDataSettings by remember { mutableStateOf(false) }

    LaunchedEffect(cacheClearStatus) {
        cacheClearStatus?.let { status ->
            Toast.makeText(context, status, Toast.LENGTH_LONG).show()
            settingsViewModel.resetCacheClearStatus()
            isClearingCache = false
        }
    }

    LaunchedEffect(showRestartMessageForDataSettings) {
        if (showRestartMessageForDataSettings) {
            Toast.makeText(
                context,
                "List loading and buffering settings may require an app restart to apply.",
                Toast.LENGTH_LONG
            ).show()
            showRestartMessageForDataSettings = false
        }
    }

    LaunchedEffect(scanStatus) {
        when (val status = scanStatus) {
            is ScanStatus.Complete -> {
                val message = if (status.importedCount > 0) {
                    "Successfully imported ${status.importedCount} file(s)!"
                } else {
                    "Scan complete. No new files found."
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                settingsViewModel.resetScanStatus()
            }

            is ScanStatus.Error -> {
                Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                settingsViewModel.resetScanStatus()
            }

            else -> { /* Idle or Scanning */
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // --- API Key Section ---
            SettingsSectionTitle(stringResource(R.string.settings_section_api_key))
            ApiKeyInputScreen(
                settingsViewModel = settingsViewModel,
                onApiKeySavedSuccessfully = {
                    onApiKeySavedRestartNeeded()
                },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider()
            SettingsSectionTitle(stringResource(R.string.settings_section_account)) // <-- Add this string

            when (val state = authState) {
                is AuthState.LoggedIn -> {
                    // Show a "Logged In" status and a Logout button
                    ListItem(
                        headlineContent = { Text("Logged In") }, // Replace with user data later
                        supportingContent = { Text("Your data is being synchronized.") },
                        leadingContent = {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null
                            )
                        }, // <-- Add this string
                        trailingContent = {
                            TextButton(onClick = { authViewModel.logout() }) {
                                Text(stringResource(R.string.action_logout)) // <-- Add this string
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    Button(
                        onClick = { settingsViewModel.triggerManualSync() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Sync Now")
                    }
                }


                is AuthState.LoggedOut, is AuthState.Error -> {
                    // Show a Login button
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.action_login)) }, // <-- Add this string
                        supportingContent = { Text(stringResource(R.string.settings_desc_login)) }, // <-- Add this string
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.Login,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable {
                            navController.navigate(AppDestinations.LOGIN_ROUTE)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (state is AuthState.Error) {
                        Text(
                            text = "Last login attempt failed: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                }

                is AuthState.InProgress -> {
                    // Show a loading state if login is in progress
                    ListItem(
                        headlineContent = { Text("Logging in...") },
                        leadingContent = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            HorizontalDivider()
            // --- Playback Settings Section (New Section Title) ---
            SettingsSectionTitle(stringResource(R.string.settings_section_playback)) // NEW string resource needed
            // NEW: Autoplay Next Video Toggle
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_label_autoplay_next_video)) }, // NEW string resource needed
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_desc_autoplay_next_video),
                        style = MaterialTheme.typography.bodySmall
                    )
                }, // NEW string resource needed
                trailingContent = {
                    Switch(
                        checked = autoplayNextVideoEnabled,
                        onCheckedChange = { isChecked ->
                            settingsViewModel.setAutoplayNextVideoEnabled(isChecked)
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Make the whole row clickable to toggle the switch
                        settingsViewModel.setAutoplayNextVideoEnabled(!autoplayNextVideoEnabled)
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_label_shuffle_on_play)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_desc_shuffle_on_play),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                trailingContent = {
                    Switch(
                        checked = shuffleOnPlayStartEnabled,
                        onCheckedChange = { settingsViewModel.setShuffleOnPlayStartEnabled(it) }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.setShuffleOnPlayStartEnabled(!shuffleOnPlayStartEnabled) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()


            // --- Data & Performance Section ---
            SettingsSectionTitle(stringResource(R.string.settings_section_data_performance))

            // Download Location Preference

            ListItem(
                headlineContent = { Text("Import Legacy Downloads") },
                supportingContent = { Text("Scan the HolodexMusic folder for any downloads not in the library.", style = MaterialTheme.typography.bodySmall) },
                leadingContent = {
                    when (scanStatus) {
                        is ScanStatus.Scanning -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else -> Icon(Icons.Default.DocumentScanner, contentDescription = null)
                    }
                },
                modifier = Modifier.clickable(enabled = scanStatus !is ScanStatus.Scanning) {
                    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }

                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        settingsViewModel.runLegacyFileScan()
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            PreferenceGroupTitle(stringResource(R.string.settings_label_download_location))
            ListItem(
                headlineContent = {
                    Text(
                        text = if (downloadLocation.isEmpty()) stringResource(R.string.settings_download_location_default) else downloadLocation,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = { Text(stringResource(R.string.settings_desc_download_location)) },
                leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                modifier = Modifier.clickable {
                    try {
                        folderPickerLauncher.launch(null) // Launch the folder picker
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to launch folder picker")
                        Toast.makeText(context, "Could not open folder picker.", Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                trailingContent = {
                    if (downloadLocation.isNotEmpty()) {
                        IconButton(onClick = { settingsViewModel.clearDownloadLocation() }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.action_clear_location)
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // Image Quality
            PreferenceGroupTitle(stringResource(R.string.settings_label_image_quality))
            PreferenceRadioGroup {
                ImageQualityOptions.entries.forEach { quality -> // Using ImageQualityOptions enum
                    PreferenceRadioButton(
                        text = quality.displayName,
                        selected = currentImageQuality == quality.key,
                        onClick = {
                            settingsViewModel.setImageQualityPreference(quality.key)
                            // Image quality changes can sometimes benefit from restart, especially for cache
                            // But usually dynamically applying size to ImageRequest is enough.
                            // Only show restart message if explicitly needed for subtle cache policy changes.
                            // For simplicity, we'll show it if user picks non-AUTO.
                            if (quality.key != AppPreferenceConstants.IMAGE_QUALITY_AUTO) {
                                showRestartMessageForDataSettings = true
                            }
                        }
                    )
                }
            }
            PreferenceDescription(stringResource(R.string.settings_desc_image_quality))

            // Audio Quality
            PreferenceGroupTitle(stringResource(R.string.settings_label_audio_quality))
            PreferenceRadioGroup {
                AudioQualityOptions.entries.forEach { quality -> // Using AudioQualityOptions enum
                    PreferenceRadioButton(
                        text = quality.displayName,
                        selected = currentAudioQuality == quality.key,
                        onClick = { settingsViewModel.setAudioQualityPreference(quality.key) }
                    )
                }
            }
            PreferenceDescription(stringResource(R.string.settings_desc_audio_quality))

            // List Loading (Paging)
            PreferenceGroupTitle(stringResource(R.string.settings_label_list_loading_config)) // New string
            PreferenceRadioGroup {
                ListLoadingConfigOptions.entries.forEach { config -> // Using ListLoadingConfigOptions enum
                    PreferenceRadioButton(
                        text = config.displayName,
                        selected = currentListLoadingConfig == config.key,
                        onClick = {
                            settingsViewModel.setListLoadingConfigPreference(config.key)
                            showRestartMessageForDataSettings =
                                true // Paging changes require restart
                        }
                    )
                }
            }
            PreferenceDescription(stringResource(R.string.settings_desc_list_loading_config)) // New string

            // Buffering Strategy (ExoPlayer)
            PreferenceGroupTitle(stringResource(R.string.settings_label_buffering_strategy)) // New string
            PreferenceRadioGroup {
                BufferingStrategyOptions.entries.forEach { strategy -> // Using BufferingStrategyOptions enum
                    PreferenceRadioButton(
                        text = strategy.displayName,
                        selected = currentBufferingStrategy == strategy.key,
                        onClick = {
                            settingsViewModel.setBufferingStrategyPreference(strategy.key)
                            showRestartMessageForDataSettings =
                                true // ExoPlayer recreation requires restart
                        }
                    )
                }
            }
            PreferenceDescription(stringResource(R.string.settings_desc_buffering_strategy)) // New string


            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            // --- Cache Management Section ---
            SettingsSectionTitle(stringResource(R.string.settings_section_cache))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Button(
                    onClick = {
                        isClearingCache = true
                        settingsViewModel.clearAllApplicationCaches()
                    },
                    enabled = !isClearingCache,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_button_clear_cache))
                }
                if (isClearingCache) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(start = 8.dp)
                    )
                }
            }
            Text(
                stringResource(R.string.settings_desc_clear_cache),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            HorizontalDivider()

            // --- Theme Selection Section ---
            SettingsSectionTitle(stringResource(R.string.settings_section_theme))
            PreferenceRadioGroup {
                ThemePreferenceOptions.entries.forEach { themeOpt ->
                    PreferenceRadioButton(
                        text = themeOpt.displayName,
                        selected = currentThemePref == themeOpt.key,
                        onClick = { settingsViewModel.setThemePreference(themeOpt.key) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            // --- About Section ---
            SettingsSectionTitle(stringResource(R.string.settings_section_about))
            InfoRow(
                label = stringResource(R.string.settings_label_version),
                value = BuildConfig.VERSION_NAME
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_label_powered_by),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            LinkItem(
                text = stringResource(R.string.settings_link_holodex),
                url = "https://holodex.net",
            )
            LinkItem(
                text = stringResource(R.string.settings_link_newpipe),
                url = "https://newpipe.net/",
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// --- Helper Composables (remain the same) ---
@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun PreferenceGroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun PreferenceDescription(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun PreferenceRadioGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.selectableGroup()) {
        content()
    }
}

@Composable
private fun PreferenceRadioButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = if (enabled) onClick else ({}),
                role = Role.RadioButton,
                enabled = enabled
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// --- Enums for Settings Options (Moved/Updated to use AppPreferenceConstants) ---
enum class ImageQualityOptions(val key: String, val displayName: String) {
    AUTO(AppPreferenceConstants.IMAGE_QUALITY_AUTO, "Auto (Recommended)"),
    MEDIUM(AppPreferenceConstants.IMAGE_QUALITY_MEDIUM, "Medium (Faster loading)"),
    LOW(AppPreferenceConstants.IMAGE_QUALITY_LOW, "Low (Data saver)")
}

enum class AudioQualityOptions(val key: String, val displayName: String) {
    BEST(AppPreferenceConstants.AUDIO_QUALITY_BEST, "Best Available"),
    STANDARD(AppPreferenceConstants.AUDIO_QUALITY_STANDARD, "Standard (~128kbps)"),
    SAVER(AppPreferenceConstants.AUDIO_QUALITY_SAVER, "Data Saver (~64kbps)")
}

enum class ListLoadingConfigOptions(val key: String, val displayName: String) {
    NORMAL(AppPreferenceConstants.LIST_LOADING_NORMAL, "Normal (Smooth scrolling)"),
    REDUCED(AppPreferenceConstants.LIST_LOADING_REDUCED, "Reduced (Less data, faster initial)"),
    MINIMAL(AppPreferenceConstants.LIST_LOADING_MINIMAL, "Minimal (Data saver, slowest scroll)")
}

enum class BufferingStrategyOptions(val key: String, val displayName: String) {
    AGGRESSIVE(AppPreferenceConstants.BUFFERING_STRATEGY_AGGRESSIVE, "Quick Start (Default)"),
    BALANCED(AppPreferenceConstants.BUFFERING_STRATEGY_BALANCED, "Balanced"),
    STABLE(AppPreferenceConstants.BUFFERING_STRATEGY_STABLE, "Stable Playback (More buffering)")
}

enum class ThemePreferenceOptions(val key: String, val displayName: String) {
    LIGHT(ThemePreference.LIGHT, "Light"),
    DARK(ThemePreference.DARK, "Dark"),
    SYSTEM(ThemePreference.SYSTEM, "Follow System")
}

// --- InfoRow and LinkItem Composables (remain the same) ---
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkItem(text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current // Ensure context is available for Toast

    ListItem(
        headlineContent = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Icon(
                Icons.Filled.Link,
                contentDescription = stringResource(R.string.content_desc_external_link)
            )
        },
        modifier = Modifier.clickable {
            try {
                uriHandler.openUri(url)
            } catch (e: Exception) {
                Timber.e(e, "Failed to open URL: $url")
                Toast.makeText(context, "Could not open link.", Toast.LENGTH_SHORT).show()
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}