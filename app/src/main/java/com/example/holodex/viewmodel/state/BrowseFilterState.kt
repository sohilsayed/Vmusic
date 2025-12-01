package com.example.holodex.viewmodel.state

enum class ViewTypePreset(
    val apiStatus: String?,
    val apiMaxUpcomingHours: Int?,
    val defaultSortField: VideoSortField,
    val defaultSortOrder: SortOrder,
    val defaultDisplayName: String
) {
    LATEST_STREAMS(
        apiStatus = "past",
        apiMaxUpcomingHours = null,
        defaultSortField = VideoSortField.AVAILABLE_AT,
        defaultSortOrder = SortOrder.DESC,
        defaultDisplayName = "Latest"
    ),
    UPCOMING_STREAMS(
        apiStatus = "upcoming",
        apiMaxUpcomingHours = 48,
        defaultSortField = VideoSortField.START_SCHEDULED,
        defaultSortOrder = SortOrder.ASC,
        defaultDisplayName = "Upcoming"
    );
}

// DELETED: enum class SongSegmentFilterMode

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

enum class SortOrder(val apiValue: String, val displayName: String) {
    ASC("asc", "Ascending"),
    DESC("desc", "Descending")
}

data class BrowseFilterState(
    val selectedOrganization: String? = null,
    val selectedPrimaryTopic: String? = null,
    val selectedViewPreset: ViewTypePreset,
    // DELETED: val songSegmentFilterMode: SongSegmentFilterMode,
    val sortField: VideoSortField,
    val sortOrder: SortOrder,
    val currentFilterDisplayName: String
) {
    val status: String? get() = selectedViewPreset.apiStatus
    val maxUpcomingHours: Int? get() = selectedViewPreset.apiMaxUpcomingHours

    companion object {
        fun create(
            preset: ViewTypePreset,
            organization: String? = null,
            primaryTopic: String? = null,
            sortFieldOverride: VideoSortField? = null,
            sortOrderOverride: SortOrder? = null
        ): BrowseFilterState {
            val effectiveSortField = sortFieldOverride ?: preset.defaultSortField
            val effectiveSortOrder = sortOrderOverride ?: preset.defaultSortOrder

            return BrowseFilterState(
                selectedOrganization = organization,
                selectedPrimaryTopic = primaryTopic,
                selectedViewPreset = preset,
                sortField = effectiveSortField,
                sortOrder = effectiveSortOrder,
                currentFilterDisplayName = preset.defaultDisplayName
            )
        }
    }

    @get:JvmName("getHasActiveFiltersProperty")
    val hasActiveFilters: Boolean
        get() {
            val default = create(this.selectedViewPreset, null, null)
            return this.selectedOrganization != default.selectedOrganization ||
                    this.selectedPrimaryTopic != default.selectedPrimaryTopic ||
                    this.sortField != default.sortField ||
                    this.sortOrder != default.sortOrder
        }

    @get:JvmName("getActiveFilterCountProperty")
    val activeFilterCount: Int
        get() {
            var count = 0
            val default = create(this.selectedViewPreset, null, null)
            if (this.selectedOrganization != default.selectedOrganization) count++
            if (this.selectedPrimaryTopic != default.selectedPrimaryTopic) count++
            if (this.sortField != default.sortField || this.sortOrder != default.sortOrder) count++
            return count
        }
}