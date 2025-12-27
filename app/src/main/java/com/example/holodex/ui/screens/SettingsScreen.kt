package com.example.holodex.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudSync
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.example.holodex.BuildConfig
import com.example.holodex.R
import com.example.holodex.auth.AuthState
import com.example.holodex.auth.AuthViewModel
import com.example.holodex.data.AppPreferenceConstants
import com.example.holodex.data.ThemePreference
import com.example.holodex.ui.composables.ApiKeyInputScreen
import com.example.holodex.ui.dialogs.AddExternalChannelDialog
import com.example.holodex.ui.navigation.AppDestinations
import com.example.holodex.viewmodel.ScanStatus
import com.example.holodex.viewmodel.SettingsSideEffect
import com.example.holodex.viewmodel.SettingsViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

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
    val state by settingsViewModel.collectAsState()

    // Local state for the dialog visibility
    // We keep this local because opening/closing a dialog is UI state, not business logic
    var showAddChannelDialog by remember { mutableStateOf(false) }

    // Dialog Composable
    if (showAddChannelDialog) {
        // The dialog uses its own Hilt ViewModel (AddChannelViewModel) internally
        // which is fully migrated to the Unified System.
        AddExternalChannelDialog(
            onDismissRequest = { showAddChannelDialog = false }
        )
    }



    var isClearingCache by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                settingsViewModel.saveDownloadLocation(uri)
            }
        }
    )
    var showRestartMessageForDataSettings by remember { mutableStateOf(false) }

    settingsViewModel.collectSideEffect { effect ->
        when(effect) {
            is SettingsSideEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.cacheClearStatus) {
        state.cacheClearStatus?.let { status ->
            Toast.makeText(context, status, Toast.LENGTH_LONG).show()
            settingsViewModel.resetCacheClearStatus()
            isClearingCache = false
        }
    }

    LaunchedEffect(state.scanStatus) {
        when (val status = state.scanStatus) {
            is ScanStatus.Complete -> {
                val message = if (status.importedCount > 0) "Successfully imported ${status.importedCount} file(s)!" else "Scan complete. No new files found."
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                settingsViewModel.resetScanStatus()
            }
            is ScanStatus.Error -> {
                Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                settingsViewModel.resetScanStatus()
            }
            else -> {}
        }
    }

    if (showRestartMessageForDataSettings) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Settings apply after app restart.", Toast.LENGTH_LONG).show()
            showRestartMessageForDataSettings = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
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
            // ... (API Key Section - Keep as is) ...
            SettingsSectionTitle(stringResource(R.string.settings_section_api_key))
            ApiKeyInputScreen(
                settingsViewModel = settingsViewModel,
                onApiKeySavedSuccessfully = { onApiKeySavedRestartNeeded() },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider()

            // --- NEW SECTION: CONTENT SOURCES ---
            SettingsSectionTitle("Content Sources")

            ListItem(
                headlineContent = { Text("Add YouTube Channel") },
                supportingContent = { Text("Import music from external YouTube channels.", style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.clickable {
                    showAddChannelDialog = true // Open Dialog
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider()

            SettingsSectionTitle(stringResource(R.string.settings_section_account))
            // ... (Account Section - Keep as is) ...
            when (val s = authState) {
                is AuthState.LoggedIn -> {
                    ListItem(
                        headlineContent = { Text("Logged In") },
                        supportingContent = { Text("Your data is being synchronized.") },
                        leadingContent = { Icon(Icons.Default.CloudSync, null) },
                        trailingContent = { TextButton(onClick = { authViewModel.logout() }) { Text(stringResource(R.string.action_logout)) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    Button(
                        onClick = { settingsViewModel.triggerManualSync() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Sync Now")
                    }
                }
                is AuthState.LoggedOut, is AuthState.Error -> {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.action_login)) },
                        supportingContent = { Text(stringResource(R.string.settings_desc_login)) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.Login, null) },
                        modifier = Modifier.clickable { navController.navigate(AppDestinations.LOGIN_ROUTE) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (s is AuthState.Error) {
                        Text("Login failed: ${s.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                    }
                }
                is AuthState.InProgress -> {
                    ListItem(headlineContent = { Text("Logging in...") }, leadingContent = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }

            HorizontalDivider()
            SettingsSectionTitle(stringResource(R.string.settings_section_playback))
            // ... (Playback Section - Keep as is) ...
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_label_autoplay_next_video)) },
                supportingContent = { Text(stringResource(R.string.settings_desc_autoplay_next_video), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Switch(
                        checked = state.autoplayEnabled,
                        onCheckedChange = { settingsViewModel.setAutoplayNextVideoEnabled(it) }
                    )
                },
                modifier = Modifier.fillMaxWidth().clickable { settingsViewModel.setAutoplayNextVideoEnabled(!state.autoplayEnabled) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_label_shuffle_on_play)) },
                supportingContent = { Text(stringResource(R.string.settings_desc_shuffle_on_play), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Switch(
                        checked = state.shuffleOnPlayStartEnabled,
                        onCheckedChange = { settingsViewModel.setShuffleOnPlayStartEnabled(it) }
                    )
                },
                modifier = Modifier.fillMaxWidth().clickable { settingsViewModel.setShuffleOnPlayStartEnabled(!state.shuffleOnPlayStartEnabled) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()


            PreferenceGroupTitle(stringResource(R.string.settings_label_download_location))
            // ... (Location, Image Quality, Audio Quality, etc. - Keep as is) ...
            ListItem(
                headlineContent = {
                    Text(if (state.downloadLocation.isEmpty()) stringResource(R.string.settings_download_location_default) else state.downloadLocation, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { Text(stringResource(R.string.settings_desc_download_location)) },
                leadingContent = { Icon(Icons.Default.FolderOpen, null) },
                modifier = Modifier.clickable { try { folderPickerLauncher.launch(null) } catch (e: Exception) { Toast.makeText(context, "Error opening picker", Toast.LENGTH_SHORT).show() } },
                trailingContent = {
                    if (state.downloadLocation.isNotEmpty()) {
                        IconButton(onClick = { settingsViewModel.clearDownloadLocation() }) { Icon(Icons.Default.Clear, stringResource(R.string.action_clear_location)) }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            PreferenceGroupTitle(stringResource(R.string.settings_label_image_quality))
            PreferenceRadioGroup {
                ImageQualityOptions.entries.forEach { quality ->
                    PreferenceRadioButton(
                        text = quality.displayName,
                        selected = state.currentImageQuality == quality.key,
                        onClick = {
                            settingsViewModel.setImageQualityPreference(quality.key)
                            if (quality.key != AppPreferenceConstants.IMAGE_QUALITY_AUTO) showRestartMessageForDataSettings = true
                        }
                    )
                }
            }
            PreferenceDescription(stringResource(R.string.settings_desc_image_quality))

            PreferenceGroupTitle(stringResource(R.string.settings_label_audio_quality))
            PreferenceRadioGroup {
                AudioQualityOptions.entries.forEach { quality ->
                    PreferenceRadioButton(
                        text = quality.displayName,
                        selected = state.currentAudioQuality == quality.key,
                        onClick = { settingsViewModel.setAudioQualityPreference(quality.key) }
                    )
                }
            }
            PreferenceDescription(stringResource(R.string.settings_desc_audio_quality))

            PreferenceGroupTitle(stringResource(R.string.settings_label_list_loading_config))
            PreferenceRadioGroup {
                ListLoadingConfigOptions.entries.forEach { config ->
                    PreferenceRadioButton(
                        text = config.displayName,
                        selected = state.currentListLoadingConfig == config.key,
                        onClick = {
                            settingsViewModel.setListLoadingConfigPreference(config.key)
                            showRestartMessageForDataSettings = true
                        }
                    )
                }
            }
            PreferenceDescription(stringResource(R.string.settings_desc_list_loading_config))

            PreferenceGroupTitle(stringResource(R.string.settings_label_buffering_strategy))
            PreferenceRadioGroup {
                BufferingStrategyOptions.entries.forEach { strategy ->
                    PreferenceRadioButton(
                        text = strategy.displayName,
                        selected = state.currentBufferingStrategy == strategy.key,
                        onClick = {
                            settingsViewModel.setBufferingStrategyPreference(strategy.key)
                            showRestartMessageForDataSettings = true
                        }
                    )
                }
            }
            PreferenceDescription(stringResource(R.string.settings_desc_buffering_strategy))

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            SettingsSectionTitle(stringResource(R.string.settings_section_cache))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Button(
                    onClick = { isClearingCache = true; settingsViewModel.clearAllApplicationCaches() },
                    enabled = !isClearingCache,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_button_clear_cache))
                }
                if (isClearingCache) CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(start = 8.dp))
            }
            Text(stringResource(R.string.settings_desc_clear_cache), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            HorizontalDivider()

            SettingsSectionTitle(stringResource(R.string.settings_section_theme))
            PreferenceRadioGroup {
                ThemePreferenceOptions.entries.forEach { themeOpt ->
                    PreferenceRadioButton(
                        text = themeOpt.displayName,
                        selected = state.currentTheme == themeOpt.key,
                        onClick = { settingsViewModel.setThemePreference(themeOpt.key) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            SettingsSectionTitle(stringResource(R.string.settings_section_about))
            InfoRow(label = stringResource(R.string.settings_label_version), value = BuildConfig.VERSION_NAME)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_label_powered_by), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            LinkItem(text = stringResource(R.string.settings_link_holodex), url = "https://holodex.net")
            LinkItem(text = stringResource(R.string.settings_link_newpipe), url = "https://newpipe.net/")
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ... (Keep all helper Composables and Enums at the bottom of the file) ...
@Composable
private fun SettingsSectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
}
@Composable
private fun PreferenceGroupTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}
@Composable
private fun PreferenceDescription(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
}
@Composable
private fun PreferenceRadioGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.selectableGroup()) { content() }
}
@Composable
private fun PreferenceRadioButton(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = if (enabled) onClick else ({}), role = Role.RadioButton, enabled = enabled),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.4f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.6f))
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkItem(text: String, url: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = { Icon(Icons.Filled.Link, stringResource(R.string.content_desc_external_link)) },
        modifier = Modifier.clickable { try { uriHandler.openUri(url) } catch (e: Exception) { Toast.makeText(context, "Could not open link.", Toast.LENGTH_SHORT).show() } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

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