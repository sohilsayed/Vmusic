// File: java/com/example/holodex/data/db/AppDatabase.kt
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
        CachedVideoEntity::class,
        CachedSongEntity::class,
        PersistedPlaybackItemEntity::class,
        PersistedPlaybackStateEntity::class,
        LikedItemEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        CachedBrowsePage::class,
        CachedSearchPage::class,
        DownloadedItemEntity::class,
        ParentVideoMetadataEntity::class,
        HistoryItemEntity::class,
        CachedDiscoveryResponse::class,
        FavoriteChannelEntity::class,
        SyncMetadataEntity::class,
        LocalFavoriteEntity::class,
        ExternalChannelEntity::class,
        StarredPlaylistEntity::class,
        LocalPlaylistEntity::class,
        LocalPlaylistItemEntity::class
    ],
    version = 21,
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
    abstract fun videoDao(): VideoDao
    abstract fun persistedPlaybackStateDao(): PersistedPlaybackStateDao
    abstract fun likedItemDao(): LikedItemDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun browsePageDao(): BrowsePageDao
    abstract fun searchPageDao(): SearchPageDao
    abstract fun downloadedItemDao(): DownloadedItemDao
    abstract fun parentVideoMetadataDao(): ParentVideoMetadataDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteChannelDao(): FavoriteChannelDao
    abstract fun discoveryDao(): DiscoveryDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun starredPlaylistDao(): StarredPlaylistDao
    abstract fun localDao(): LocalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null


        val MIGRATION_18_19: Migration = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No operation needed. The schema for version 18 is identical to the
                // final schema for version 19. This migration simply tells Room
                // that the transition is safe and requires no changes.
            }
        }
        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_items` ADD COLUMN `is_local_only` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `local_favorites` (
                        `itemId` TEXT NOT NULL, 
                        `videoId` TEXT NOT NULL, 
                        `channelId` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `artistText` TEXT NOT NULL, 
                        `artworkUrl` TEXT, 
                        `durationSec` INTEGER NOT NULL, 
                        `isSegment` INTEGER NOT NULL, 
                        `songStartSec` INTEGER, 
                        `songEndSec` INTEGER, 
                        PRIMARY KEY(`itemId`)
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `external_channels` (
                        `channelId` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `photoUrl` TEXT, 
                        `lastCheckedTimestamp` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `errorCount` INTEGER NOT NULL, 
                        PRIMARY KEY(`channelId`)
                    )
                """)

                // *** THE FIX IS HERE ***
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `local_playlists` (
                        `localPlaylistId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
                // *** END OF FIX ***

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `local_playlist_items` (
                        `playlistOwnerId` INTEGER NOT NULL,
                        `itemId` TEXT NOT NULL,
                        `videoId` TEXT NOT NULL,
                        `itemOrder` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artistText` TEXT NOT NULL,
                        `artworkUrl` TEXT,
                        `durationSec` INTEGER NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `isSegment` INTEGER NOT NULL,
                        `songStartSec` INTEGER,
                        `songEndSec` INTEGER,
                        PRIMARY KEY(`playlistOwnerId`, `itemId`)
                    )
                """)
            }
        }
        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- FIX FOR local_playlists TABLE ---
                db.execSQL("ALTER TABLE `local_playlists` RENAME TO `local_playlists_old`")
                db.execSQL("""
                    CREATE TABLE `local_playlists` (
                        `localPlaylistId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                """)
                // Copy data, providing a default for the new column.
                // Note: We use System.currentTimeMillis() for createdAt to give old data a timestamp.
                db.execSQL("""
                    INSERT INTO `local_playlists` (localPlaylistId, name, description, createdAt)
                    SELECT playlistId, name, description, ${System.currentTimeMillis()} FROM `local_playlists_old`
                """)
                db.execSQL("DROP TABLE `local_playlists_old`")

                // --- NEW FIX FOR local_playlist_items TABLE ---
                // This table was also created incorrectly in the 19->20 migration. We fix it here.
                db.execSQL("DROP TABLE IF EXISTS `local_playlist_items`") // It contains no user data yet, so dropping is safe.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `local_playlist_items` (
                        `playlistOwnerId` INTEGER NOT NULL,
                        `itemId` TEXT NOT NULL,
                        `videoId` TEXT NOT NULL,
                        `itemOrder` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artistText` TEXT NOT NULL,
                        `artworkUrl` TEXT,
                        `durationSec` INTEGER NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `isSegment` INTEGER NOT NULL,
                        `songStartSec` INTEGER,
                        `songEndSec` INTEGER,
                        PRIMARY KEY(`playlistOwnerId`, `itemId`)
                    )
                """)
            }
        }
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "holodex_music_app_database"
                )
                    .addMigrations(MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21
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
