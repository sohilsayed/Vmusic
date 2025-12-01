package com.example.holodex.data.db

enum class LikedItemType {
    VIDEO,
    SONG_SEGMENT
}

enum class DownloadStatus {
    NOT_DOWNLOADED,
    ENQUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PROCESSING,
    EXPORT_FAILED,
    PAUSED,
    DELETING
}

enum class SyncStatus {
    SYNCED,
    DIRTY,
    PENDING_DELETE
}