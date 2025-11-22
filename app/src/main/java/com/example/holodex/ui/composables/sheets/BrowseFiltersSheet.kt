@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.holodex.ui.composables.sheets// File: java\com\example\holodex\ui\composables\sheets\BrowseFiltersSheet.kt
// ... (imports)import com.example.holodex.viewmodel.state.SongSegmentFilterMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.holodex.R
import com.example.holodex.viewmodel.VideoListViewModel
import com.example.holodex.viewmodel.state.BrowseFilterState
import com.example.holodex.viewmodel.state.SortOrder
import com.example.holodex.viewmodel.state.VideoSortField
import com.example.holodex.viewmodel.state.ViewTypePreset


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseFiltersSheet(
    initialFilters: BrowseFilterState,
    onFiltersApplied: (BrowseFilterState) -> Unit,
    onDismiss: () -> Unit,
    videoListViewModel: VideoListViewModel = hiltViewModel()
) {
    var tempFilters by remember { mutableStateOf(initialFilters) }

    val organizationsForDropdown by videoListViewModel.availableOrganizations.collectAsStateWithLifecycle()

    val viewTypePresetOptions = videoListViewModel.browseScreenCategories

    var viewPresetExpanded by remember { mutableStateOf(false) }
    var orgExpanded by remember { mutableStateOf(false) }
    var sortFieldExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Filter & Sort Music Streams", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))

        FilterSectionHeader("View As")
        val currentViewPresetDisplay by remember(tempFilters, organizationsForDropdown) {
            derivedStateOf {
                val orgDisplayPart = if (tempFilters.selectedOrganization != null) {
                    val orgName = organizationsForDropdown.find { it.second == tempFilters.selectedOrganization }?.first ?: tempFilters.selectedOrganization!!
                    if (orgName != "All Vtubers") " - $orgName" else ""
                } else ""

                val segmentDisplayPart = if (tempFilters.selectedViewPreset == ViewTypePreset.LATEST_STREAMS) {
                    tempFilters.songSegmentFilterMode.displayNameSuffix ?: ""
                } else {
                    ""
                }
                "${tempFilters.selectedViewPreset.defaultDisplayName}$orgDisplayPart$segmentDisplayPart"
            }
        }

        ExposedDropdownMenuBox(
            expanded = viewPresetExpanded,
            onExpandedChange = { viewPresetExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentViewPresetDisplay,
                onValueChange = {}, readOnly = true, label = { Text("View Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewPresetExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = viewPresetExpanded, onDismissRequest = { viewPresetExpanded = false }) {
                viewTypePresetOptions.forEach { (displayName, presetBrowseFilterStateFromVM) ->
                    DropdownMenuItem(
                        text = { Text(displayName) },
                        onClick = {
                            val currentOrgApiVal = tempFilters.selectedOrganization
                            val currentPrimaryTopic = tempFilters.selectedPrimaryTopic // Preserve topic if any
                            val currentSortField = tempFilters.sortField
                            val currentSortOrder = tempFilters.sortOrder

                            var newFilterState = BrowseFilterState.create(
                                preset = presetBrowseFilterStateFromVM.selectedViewPreset,
                                songFilterMode = presetBrowseFilterStateFromVM.songSegmentFilterMode,
                                organization = currentOrgApiVal,
                                primaryTopic = currentPrimaryTopic,
                                // Maintain existing sort if it makes sense for the new preset, otherwise use preset default
                                sortFieldOverride = if (presetBrowseFilterStateFromVM.selectedViewPreset == ViewTypePreset.LATEST_STREAMS && (currentSortField == VideoSortField.START_SCHEDULED || currentSortField == VideoSortField.LIVE_VIEWERS)) null else currentSortField,
                                sortOrderOverride = if (presetBrowseFilterStateFromVM.selectedViewPreset == ViewTypePreset.LATEST_STREAMS && (currentSortField == VideoSortField.START_SCHEDULED || currentSortField == VideoSortField.LIVE_VIEWERS)) null else currentSortOrder,
                            )

                            // Post-adjustment: if the chosen preset changed the sort field to something incompatible,
                            // force it to a default compatible sort for the new preset.
                            if (newFilterState.selectedViewPreset == ViewTypePreset.LATEST_STREAMS) {
                                if (newFilterState.sortField == VideoSortField.START_SCHEDULED || newFilterState.sortField == VideoSortField.LIVE_VIEWERS) {
                                    newFilterState = newFilterState.copy(
                                        sortField = VideoSortField.AVAILABLE_AT, // Default to a valid sort for LATEST
                                        sortOrder = SortOrder.DESC
                                    )
                                }
                            } else if (newFilterState.selectedViewPreset == ViewTypePreset.UPCOMING_STREAMS) {
                                if (newFilterState.sortField != VideoSortField.START_SCHEDULED && newFilterState.sortField != VideoSortField.LIVE_VIEWERS) {
                                    newFilterState = newFilterState.copy(
                                        sortField = VideoSortField.START_SCHEDULED,
                                        sortOrder = SortOrder.ASC // Upcoming usually sorted by earliest scheduled first
                                    )
                                }
                            }

                            tempFilters = newFilterState
                            viewPresetExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        FilterSectionHeader("Organization")
        val currentOrgDisplay = organizationsForDropdown.find { it.second == tempFilters.selectedOrganization }?.first ?: "All Tracked Orgs"
        ExposedDropdownMenuBox(expanded = orgExpanded, onExpandedChange = { orgExpanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = currentOrgDisplay, onValueChange = {}, readOnly = true, label = { Text("Organization") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = orgExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = orgExpanded, onDismissRequest = { orgExpanded = false }) {
                // --- FIX: Iterate over the collected list ---
                organizationsForDropdown.forEach { (name, value) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        tempFilters = BrowseFilterState.create(
                            preset = tempFilters.selectedViewPreset,
                            songFilterMode = tempFilters.songSegmentFilterMode,
                            organization = value,
                            primaryTopic = tempFilters.selectedPrimaryTopic,
                            sortFieldOverride = tempFilters.sortField,
                            sortOrderOverride = tempFilters.sortOrder,
                        )
                        orgExpanded = false
                    })
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        FilterSectionHeader("Sort By")
        ExposedDropdownMenuBox(expanded = sortFieldExpanded, onExpandedChange = { sortFieldExpanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = tempFilters.sortField.displayName, onValueChange = {}, readOnly = true, label = { Text("Sort Field") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortFieldExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = sortFieldExpanded, onDismissRequest = { sortFieldExpanded = false }) {
                VideoSortField.entries.forEach { field ->
                    val isApplicable = when (tempFilters.selectedViewPreset) {
                        ViewTypePreset.UPCOMING_STREAMS -> {
                            field == VideoSortField.START_SCHEDULED ||
                                    field == VideoSortField.LIVE_VIEWERS ||
                                    field == VideoSortField.TITLE ||
                                    field == VideoSortField.PUBLISHED_AT
                        }
                        ViewTypePreset.LATEST_STREAMS -> {
                            field != VideoSortField.START_SCHEDULED && field != VideoSortField.LIVE_VIEWERS
                        }
                    }

                    if (isApplicable) {
                        DropdownMenuItem(text = { Text(field.displayName) }, onClick = {
                            var newSortOrder = tempFilters.sortOrder
                            // Automatically set default sort order based on field
                            if (field == VideoSortField.AVAILABLE_AT || field == VideoSortField.PUBLISHED_AT || field == VideoSortField.SONG_COUNT) {
                                newSortOrder = SortOrder.DESC
                            } else if (field == VideoSortField.START_SCHEDULED) {
                                newSortOrder = SortOrder.ASC
                            } else if (field == VideoSortField.LIVE_VIEWERS) {
                                newSortOrder = SortOrder.DESC
                            }

                            tempFilters = tempFilters.copy(sortField = field, sortOrder = newSortOrder)
                            sortFieldExpanded = false
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SortOrder.entries.forEach { order ->
                Row(Modifier.clickable { tempFilters = tempFilters.copy(sortOrder = order) }.padding(horizontal = 8.dp, vertical = 4.dp).weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = tempFilters.sortOrder == order, onClick = { tempFilters = tempFilters.copy(sortOrder = order) })
                    Text(order.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                onFiltersApplied(tempFilters)
            }) { Text(stringResource(id = R.string.apply_filters_button)) }
        }
    }
}

@Composable
fun FilterSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
    )
}