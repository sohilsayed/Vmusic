package com.example.holodex.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.holodex.playback.data.model.PersistedPlaybackItemEntity
import com.example.holodex.playback.data.model.PersistedPlaybackStateDao
import com.example.holodex.playback.data.model.PersistedPlaybackStateEntity

@Database(
    entities = [
        // --- NEW UNIFIED ENTITIES ---
        UnifiedMetadataEntity::class,
        UserInteractionEntity::class,

        CachedVideoEntity::class,
        CachedSongEntity::class,
        PersistedPlaybackItemEntity::class,
        PersistedPlaybackStateEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        CachedBrowsePage::class,
        CachedSearchPage::class,
        DownloadedItemEntity::class,
        ParentVideoMetadataEntity::class,
        HistoryItemEntity::class,
        CachedDiscoveryResponse::class,
        SyncMetadataEntity::class,
        StarredPlaylistEntity::class,
        LocalPlaylistEntity::class,
        LocalPlaylistItemEntity::class
    ],
    version = 23, // INCREMENTED VERSION
    exportSchema = true
)
@TypeConverters(
    HolodexSongListConverter::class,
    LikedItemTypeConverter::class,
    HolodexVideoItemListConverter::class,
    StringListConverter::class,
    DiscoveryResponseConverter::class,
    DownloadStatusConverter::class,
    SyncStatusConverter::class
)
abstract class AppDatabase : RoomDatabase() {

    // --- NEW DAO ---
    abstract fun unifiedDao(): UnifiedDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun browsePageDao(): BrowsePageDao
    abstract fun searchPageDao(): SearchPageDao
    abstract fun downloadedItemDao(): DownloadedItemDao
    abstract fun parentVideoMetadataDao(): ParentVideoMetadataDao
    abstract fun historyDao(): HistoryDao
    abstract fun videoDao(): VideoDao
    abstract fun discoveryDao(): DiscoveryDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun starredPlaylistDao(): StarredPlaylistDao
    abstract fun persistedPlaybackStateDao(): PersistedPlaybackStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_18_19: Migration = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) { }
        }
        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_items` ADD COLUMN `is_local_only` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_favorites` (`itemId` TEXT NOT NULL, `videoId` TEXT NOT NULL, `channelId` TEXT NOT NULL, `title` TEXT NOT NULL, `artistText` TEXT NOT NULL, `artworkUrl` TEXT, `durationSec` INTEGER NOT NULL, `isSegment` INTEGER NOT NULL, `songStartSec` INTEGER, `songEndSec` INTEGER, PRIMARY KEY(`itemId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `external_channels` (`channelId` TEXT NOT NULL, `name` TEXT NOT NULL, `photoUrl` TEXT, `lastCheckedTimestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `errorCount` INTEGER NOT NULL, PRIMARY KEY(`channelId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_playlists` (`localPlaylistId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_playlist_items` (`playlistOwnerId` INTEGER NOT NULL, `itemId` TEXT NOT NULL, `videoId` TEXT NOT NULL, `itemOrder` INTEGER NOT NULL, `title` TEXT NOT NULL, `artistText` TEXT NOT NULL, `artworkUrl` TEXT, `durationSec` INTEGER NOT NULL, `channelId` TEXT NOT NULL, `isSegment` INTEGER NOT NULL, `songStartSec` INTEGER, `songEndSec` INTEGER, PRIMARY KEY(`playlistOwnerId`, `itemId`))")
            }
        }
        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local_playlists` RENAME TO `local_playlists_old`")
                db.execSQL("CREATE TABLE `local_playlists` (`localPlaylistId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `createdAt` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `local_playlists` (localPlaylistId, name, description, createdAt) SELECT playlistId, name, description, ${System.currentTimeMillis()} FROM `local_playlists_old`")
                db.execSQL("DROP TABLE `local_playlists_old`")
                db.execSQL("DROP TABLE IF EXISTS `local_playlist_items`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_playlist_items` (`playlistOwnerId` INTEGER NOT NULL, `itemId` TEXT NOT NULL, `videoId` TEXT NOT NULL, `itemOrder` INTEGER NOT NULL, `title` TEXT NOT NULL, `artistText` TEXT NOT NULL, `artworkUrl` TEXT, `durationSec` INTEGER NOT NULL, `channelId` TEXT NOT NULL, `isSegment` INTEGER NOT NULL, `songStartSec` INTEGER, `songEndSec` INTEGER, PRIMARY KEY(`playlistOwnerId`, `itemId`))")
            }
        }

        // *** NEW MIGRATION: 21 -> 22 (FIXED) ***
        val MIGRATION_21_22: Migration = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create Tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `unified_metadata` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `artistName` TEXT NOT NULL, 
                        `type` TEXT NOT NULL,
                        `specificArtUrl` TEXT, 
                        `uploaderAvatarUrl` TEXT, 
                        `duration` INTEGER NOT NULL,
                        `startSeconds` INTEGER DEFAULT NULL, 
                        `endSeconds` INTEGER DEFAULT NULL, 
                        `parentVideoId` TEXT,
                        `channelId` TEXT NOT NULL, 
                        `org` TEXT, 
                        `topicId` TEXT, 
                        `status` TEXT NOT NULL DEFAULT 'past',
                        `availableAt` TEXT, 
                        `publishedAt` TEXT, 
                        `songCount` INTEGER NOT NULL DEFAULT 0,
                        `description` TEXT, 
                        `lastUpdatedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_interactions` (
                        `itemId` TEXT NOT NULL, 
                        `interactionType` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL,
                        `localFilePath` TEXT, 
                        `downloadStatus` TEXT, 
                        `downloadFileName` TEXT, 
                        `downloadTrackNum` INTEGER,
                        `downloadTargetFormat` TEXT, 
                        `downloadProgress` INTEGER NOT NULL DEFAULT 0,
                        `serverId` TEXT, 
                        `syncStatus` TEXT NOT NULL DEFAULT 'SYNCED',
                        PRIMARY KEY(`itemId`, `interactionType`),
                        FOREIGN KEY(`itemId`) REFERENCES `unified_metadata`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_interactions_itemId` ON `user_interactions` (`itemId`)")

                // 3. MIGRATE: Videos -> Metadata
                // FIX: Mapped column names from Room Entity (e.g. 'topic_id') to New Entity (e.g. 'topicId')
                db.execSQL("""
                    INSERT OR IGNORE INTO unified_metadata 
                    (id, title, artistName, type, channelId, org, uploaderAvatarUrl, duration, description, topicId, status, availableAt, publishedAt, songCount, lastUpdatedAt)
                    SELECT id, title, channel_name, 'VIDEO', channel_id, channel_org, channel_photoUrl, duration, description, topic_id, status, available_at, published_at, song_count, fetched_at_ms 
                    FROM videos
                """)

                // 4. MIGRATE: Liked Items
                db.execSQL("""
                    INSERT OR IGNORE INTO unified_metadata 
                    (id, title, artistName, type, specificArtUrl, parentVideoId, channelId, duration, startSeconds, endSeconds, lastUpdatedAt)
                    SELECT itemId, 
                           COALESCE(actual_song_name, title_snapshot), 
                           COALESCE(actual_song_artist, artist_text_snapshot), 
                           CASE WHEN item_type = 'SONG_SEGMENT' THEN 'SEGMENT' ELSE 'VIDEO' END,
                           actual_song_artwork_url, 
                           videoId, 
                           channel_id_snapshot, duration_sec_snapshot, 
                           song_start_seconds, song_end_seconds, last_modified_at
                    FROM liked_items
                """)

                db.execSQL("""
                    INSERT OR IGNORE INTO user_interactions (itemId, interactionType, timestamp, serverId, syncStatus)
                    SELECT itemId, 'LIKE', liked_at, server_id, sync_status FROM liked_items
                """)

                // 5. MIGRATE: Downloads
                db.execSQL("""
                    INSERT OR IGNORE INTO unified_metadata 
                    (id, title, artistName, type, specificArtUrl, parentVideoId, channelId, duration, startSeconds, endSeconds, lastUpdatedAt)
                    SELECT videoId, title, artistText, 'SEGMENT', artworkUrl, 
                           substr(videoId, 0, instr(videoId, '_') - 1), 
                           channelId, durationSec, 
                           CAST(substr(videoId, instr(videoId, '_') + 1) AS INTEGER), 
                           CAST(substr(videoId, instr(videoId, '_') + 1) AS INTEGER) + durationSec, 
                           downloadedAt 
                    FROM downloaded_items
                """)

                db.execSQL("""
                    INSERT OR IGNORE INTO user_interactions 
                    (itemId, interactionType, timestamp, localFilePath, downloadStatus, downloadFileName, downloadTrackNum, downloadTargetFormat, downloadProgress, syncStatus)
                    SELECT videoId, 'DOWNLOAD', downloadedAt, localFileUri, downloadStatus, fileName, track_number, targetFormat, progress, 'SYNCED' 
                    FROM downloaded_items
                """)

                // 6. MIGRATE: History
                db.execSQL("""
                    INSERT OR IGNORE INTO unified_metadata 
                    (id, title, artistName, type, specificArtUrl, parentVideoId, channelId, duration, startSeconds, endSeconds, lastUpdatedAt)
                    SELECT itemId, title, artistText, 'SEGMENT', artworkUrl, videoId, channelId, durationSec, 
                           songStartSeconds, songStartSeconds + durationSec, playedAtTimestamp
                    FROM history_items
                """)

                db.execSQL("""
                    INSERT OR IGNORE INTO user_interactions (itemId, interactionType, timestamp, syncStatus)
                    SELECT itemId, 'HISTORY', playedAtTimestamp, 'SYNCED' FROM history_items
                """)
            }
        }
        val MIGRATION_22_23: Migration = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()

                // 1. Migrate Local Favorites -> Unified
                // These are "Local Only", so we mark them DIRTY but they likely won't sync without a server ID.
                // We treat them as SEGMENTs or VIDEOs based on flags.
                db.execSQL("""
                    INSERT OR IGNORE INTO unified_metadata 
                    (id, title, artistName, type, specificArtUrl, parentVideoId, channelId, duration, startSeconds, endSeconds, lastUpdatedAt)
                    SELECT itemId, title, artistText, 
                           CASE WHEN isSegment = 1 THEN 'SEGMENT' ELSE 'VIDEO' END,
                           artworkUrl, videoId, channelId, durationSec, 
                           songStartSec, songEndSec, $now
                    FROM local_favorites
                """)

                db.execSQL("""
                    INSERT OR IGNORE INTO user_interactions (itemId, interactionType, timestamp, syncStatus)
                    SELECT itemId, 'LIKE', $now, 'DIRTY' FROM local_favorites
                """)

                // 2. Migrate External Channels -> Unified
                db.execSQL("""
                    INSERT OR IGNORE INTO unified_metadata 
                    (id, title, artistName, type, specificArtUrl, uploaderAvatarUrl, duration, channelId, lastUpdatedAt)
                    SELECT channelId, name, 'External', 'CHANNEL', photoUrl, photoUrl, 0, channelId, lastCheckedTimestamp
                    FROM external_channels
                """)

                db.execSQL("""
                    INSERT OR IGNORE INTO user_interactions (itemId, interactionType, timestamp, syncStatus)
                    SELECT channelId, 'FAV_CHANNEL', lastCheckedTimestamp, 'DIRTY' FROM external_channels
                """)

                // 3. DROP OLD TABLES
                db.execSQL("DROP TABLE IF EXISTS local_favorites")
                db.execSQL("DROP TABLE IF EXISTS external_channels")
                db.execSQL("DROP TABLE IF EXISTS liked_items")
                db.execSQL("DROP TABLE IF EXISTS favorite_channels")
            }
        }
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "holodex_music_app_database"
                )
                    .addMigrations(
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class DownloadStatusConverter {
    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus?): String? {
        return value?.name
    }

    @TypeConverter
    fun toDownloadStatus(value: String?): DownloadStatus? {
        return value?.let {
            try {
                DownloadStatus.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

class LikedItemTypeConverter {
    @TypeConverter
    fun fromLikedItemType(value: LikedItemType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toLikedItemType(value: String?): LikedItemType? {
        return value?.let {
            try {
                LikedItemType.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
class SyncStatusConverter {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus?): String? {
        return value?.name
    }

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? {
        return value?.let {
            try {
                SyncStatus.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
