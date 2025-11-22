package com.example.holodex.viewmodel.state

// Enum for the main view type, which dictates API parameters like 'status'
enum class ViewTypePreset(
    val apiStatus: String?, // e.g., "past", "upcoming"
    val apiMaxUpcomingHours: Int?,
    val defaultSortField: VideoSortField,
    val defaultSortOrder: SortOrder,
    val defaultDisplayName: String // Base display name for this preset
) {
    LATEST_STREAMS(
        apiStatus = "past",
        apiMaxUpcomingHours = null,
        defaultSortField = VideoSortField.AVAILABLE_AT,
        defaultSortOrder = SortOrder.DESC,
        defaultDisplayName = "Latest Music Streams"
    ),
    UPCOMING_STREAMS(
        apiStatus = "upcoming",
        apiMaxUpcomingHours = 48,
        defaultSortField = VideoSortField.START_SCHEDULED,
        defaultSortOrder = SortOrder.ASC,
        defaultDisplayName = "Upcoming Music (Next 48h)"
    );
}

// Enum for client-side filtering based on song segments
enum class SongSegmentFilterMode(val displayNameSuffix: String?) {
    ALL(null), // Show all (after initial API and music content filtering)
    REQUIRE_SONGS(" (with segments)"),
    EXCLUDE_SONGS(" (without segments)");
}

// Enum for API sort fields
enum class VideoSortField(val apiValue: String, val displayName: String) {
    AVAILABLE_AT("available_at", "Date"),
    PUBLISHED_AT("published_at", "Published Date"),
    START_SCHEDULED("start_scheduled", "Scheduled Time"),
    START_ACTUAL("start_actual", "Actual Start"),
    DURATION("duration", "Duration"),
    LIVE_VIEWERS("live_viewers", "Live Viewers"),
    SONG_COUNT("songcount", "Song Count"),
    TITLE("title", "Title")
}

// Enum for API sort order
enum class SortOrder(val apiValue: String, val displayName: String) {
    ASC("asc", "Ascending"),
    DESC("desc", "Descending")
}

data class BrowseFilterState(
    val selectedOrganization: String? = null, // API value of the org (e.g., "Hololive")
    val selectedPrimaryTopic: String? = null, // API value of the topic (e.g., "singing")

    val selectedViewPreset: ViewTypePreset,
    val songSegmentFilterMode: SongSegmentFilterMode, // Client-side filter for LATEST_STREAMS

    val sortField: VideoSortField,
    val sortOrder: SortOrder,

    val currentFilterDisplayName: String
) {
    // Computed properties for API parameters, derived from selectedViewPreset
    val status: String? get() = selectedViewPreset.apiStatus
    val maxUpcomingHours: Int? get() = selectedViewPreset.apiMaxUpcomingHours

    companion object {

        fun create(
            preset: ViewTypePreset,
            songFilterMode: SongSegmentFilterMode,
            organization: String? = null,
            primaryTopic: String? = null,
            sortFieldOverride: VideoSortField? = null,
            sortOrderOverride: SortOrder? = null
        ): BrowseFilterState {
            val effectiveSortField = sortFieldOverride ?: preset.defaultSortField
            val effectiveSortOrder = sortOrderOverride ?: preset.defaultSortOrder

            // The display name will now be generated inside the Composable.
            // We store a placeholder or base name here.
            val displayName = preset.defaultDisplayName

            return BrowseFilterState(
                selectedOrganization = organization,
                selectedPrimaryTopic = primaryTopic,
                selectedViewPreset = preset,
                songSegmentFilterMode = songFilterMode,
                sortField = effectiveSortField,
                sortOrder = effectiveSortOrder,
                currentFilterDisplayName = displayName // This will be updated by the UI
            )
        }
        // --- END OF MODIFICATION ---
    }

    // Helper to check if any non-default filters are active
    @get:JvmName("getHasActiveFiltersProperty")
    val hasActiveFilters: Boolean
        get() {
            val defaultForPreset = create(
                preset = this.selectedViewPreset,
                songFilterMode = SongSegmentFilterMode.ALL, // Default song filter for comparison
                organization = null,
                primaryTopic = null,
                sortFieldOverride = this.selectedViewPreset.defaultSortField,
                sortOrderOverride = this.selectedViewPreset.defaultSortOrder,
            )

            return this.selectedOrganization != defaultForPreset.selectedOrganization ||
                    this.selectedPrimaryTopic != defaultForPreset.selectedPrimaryTopic ||
                    this.sortField != defaultForPreset.sortField ||
                    this.sortOrder != defaultForPreset.sortOrder ||
                    (this.selectedViewPreset == ViewTypePreset.LATEST_STREAMS && this.songSegmentFilterMode != SongSegmentFilterMode.ALL) ||
                    this.selectedViewPreset != ViewTypePreset.LATEST_STREAMS // if it's not the absolute default view preset
        }

    // Helper to count active filters for badge
    @get:JvmName("getActiveFilterCountProperty")
    val activeFilterCount: Int
        get() {
            var count = 0
            val defaultForPreset = create( // Create a default state for the current preset for comparison
                preset = this.selectedViewPreset,
                songFilterMode = SongSegmentFilterMode.ALL,
                organization = null,
                primaryTopic = null,
                sortFieldOverride = this.selectedViewPreset.defaultSortField,
                sortOrderOverride = this.selectedViewPreset.defaultSortOrder,
            )

            if (this.selectedOrganization != defaultForPreset.selectedOrganization) count++
            if (this.selectedPrimaryTopic != defaultForPreset.selectedPrimaryTopic) count++ // If topics are ever user-selectable beyond defaults
            if (this.sortField != defaultForPreset.sortField || this.sortOrder != defaultForPreset.sortOrder) count++

            // Count song segment filter only if it's for LATEST_STREAMS and not ALL
            if (this.selectedViewPreset == ViewTypePreset.LATEST_STREAMS && this.songSegmentFilterMode != SongSegmentFilterMode.ALL) {
                count++
            }
            // If the view preset itself is not the "default" (e.g., LATEST_STREAMS with ALL segments), count it.
            // This logic might need adjustment based on what you consider a "base default".
            // For now, let's say changing the view preset is a filter.
            if (this.selectedViewPreset != ViewTypePreset.LATEST_STREAMS) { // Assuming LATEST_STREAMS with ALL segments is the "truest" default.
                count++
            } else if (this.selectedViewPreset == ViewTypePreset.LATEST_STREAMS && this.songSegmentFilterMode != SongSegmentFilterMode.ALL){
                // if it IS latest streams, but song filter is active, it's already counted.
                // This else-if prevents double counting if the default view is LATEST + ALL and song_filter is active.
            }


            return count
        }
}