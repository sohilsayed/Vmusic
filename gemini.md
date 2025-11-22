# Holodex Project Migration Roadmap

This document outlines the current migration status for key architectural changes in the Holodex Android application.

## Phase 1: Orbit MVI Migration

The goal of this phase is to migrate all ViewModels to use the Orbit MVI pattern for state management.

### Status: In Progress

- **Total ViewModels identified for migration:** 14 (as of latest analysis)
- **Migrated ViewModels:** 1
    - ✅ `FavoritesViewModel.kt`

- **ViewModels Pending Migration:** 13
    - ⏳ `ChannelDetailsViewModel.kt`
    - ⏳ `DiscoveryViewModel.kt`
    - ⏳ `DownloadsViewModel.kt`
    - ⏳ `ExternalChannelViewModel.kt`
    - ⏳ `FullListViewModel.kt`
    - ⏳ `FullPlayerViewModel.kt`
    - ⏳ `HistoryViewModel.kt`
    - ⏳ `PlaybackViewModel.kt`
    - ⏳ `PlaylistDetailsViewModel.kt`
    - ⏳ `PlaylistManagementViewModel.kt`
    - ⏳ `SettingsViewModel.kt`
    - ⏳ `VideoDetailsViewModel.kt`
    - ⏳ `VideoListViewModel.kt` (High priority: mentioned in `sync rules.txt` as a complex screen)

## Phase 2: Store5 Migration

The goal of this phase is to fully integrate the Store5 library for robust data loading and caching.

### Status: Pending

- **Total Store5 related tasks:** 3 (as identified in previous `gemini.md` analysis)
- **Completed Store5 tasks:** 0
- **Progress:** The project has encountered build errors related to Store5 integration, indicating an incomplete migration or API changes. Further investigation and implementation are required.

---

**Next Steps:**
- Continue with Orbit MVI migration, prioritizing `VideoListViewModel.kt`.
- Address the Store5 migration issues to ensure proper data flow and caching.
