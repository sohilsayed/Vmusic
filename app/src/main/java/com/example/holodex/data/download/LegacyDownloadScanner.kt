// File: java/com/example/holodex/data/download/LegacyDownloadScanner.kt
package com.example.holodex.data.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.db.UnifiedMetadataEntity
import com.example.holodex.data.db.UserInteractionEntity
import com.example.holodex.data.model.HolodexSong
import com.example.holodex.data.model.HolodexVideoItem
import com.example.holodex.data.model.SearchCondition
import com.example.holodex.data.model.VideoSearchRequest
import com.example.holodex.data.repository.HolodexRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class LegacyDownloadScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holodexRepository: HolodexRepository,
    private val unifiedDao: UnifiedDao // Injected UnifiedDao instead of removed DAO
) {
    companion object {
        private const val TAG = "LegacyDownloadScanner"
        private const val DOWNLOAD_FOLDER_NAME = "HolodexMusic"
        private const val SIMILARITY_THRESHOLD = 0.85 // 85% similarity needed for a match
        private const val CONCURRENT_SCANS_LIMIT = 4 // Process up to 4 videos at a time
    }

    private data class FileInfo(val uri: Uri, val name: String, val lastModified: Long)
    private data class FileMetadata(val title: String, val artist: String, val album: String)
    private data class FileWithMetadata(val fileInfo: FileInfo, val metadata: FileMetadata)

    suspend fun scanAndImportLegacyDownloads(): Int = withContext(Dispatchers.IO) {
        var totalImportedCount = 0
        try {
            val potentialLegacyFiles = queryMediaStoreForAppDownloads()
            if (potentialLegacyFiles.isEmpty()) {
                Timber.d("$TAG: No .m4a files found via MediaStore.")
                return@withContext 0
            }

            // FIX: Use UnifiedDao to get existing download filenames
            val existingDbFiles = unifiedDao.getAllDownloadsOneShot()
                .mapNotNull { it.downloadFileName }
                .toSet()

            val filesToProcess = potentialLegacyFiles.filterNot { existingDbFiles.contains(it.name) }

            if (filesToProcess.isEmpty()) {
                Timber.i("$TAG: All ${potentialLegacyFiles.size} files are already in the database.")
                return@withContext 0
            }

            Timber.i("$TAG: Found ${filesToProcess.size} new potential legacy files. Grouping by album...")

            val filesGroupedByAlbum = filesToProcess.mapNotNull { fileInfo ->
                extractMetadata(fileInfo.uri)?.let { metadata -> FileWithMetadata(fileInfo, metadata) }
            }.groupBy { it.metadata.album }

            Timber.d("$TAG: Grouped files into ${filesGroupedByAlbum.size} potential videos. Starting concurrent scan...")

            val semaphore = Semaphore(CONCURRENT_SCANS_LIMIT)

            totalImportedCount = coroutineScope {
                val deferredImports = filesGroupedByAlbum.map { (albumTitle, filesInAlbum) ->
                    async {
                        var groupImportCount = 0
                        semaphore.acquire() // Wait for a permit to start
                        try {
                            val artist = filesInAlbum.first().metadata.artist
                            val videoWithSongs = findVideoForGroup(albumTitle, artist)

                            if (videoWithSongs != null) {
                                for (fileWithMeta in filesInAlbum) {
                                    val matchedSong = findMatchingSong(fileWithMeta.metadata.title, videoWithSongs.songs)
                                    if (matchedSong != null) {
                                        importSong(fileWithMeta.fileInfo, videoWithSongs, matchedSong)
                                        groupImportCount++
                                    } else {
                                        Timber.w("$TAG: FAILED to find song match for '${fileWithMeta.metadata.title}' in video '${videoWithSongs.title}'")
                                    }
                                }
                                if (groupImportCount > 0) {
                                    Timber.i("$TAG: Imported $groupImportCount songs for video '${videoWithSongs.title}'")
                                }
                            } else {
                                Timber.w("$TAG: FAILED to find a parent video for group with album title: '$albumTitle'")
                            }
                        } finally {
                            semaphore.release() // Always release the permit
                        }
                        groupImportCount // Return the count for this group
                    }
                }
                deferredImports.awaitAll().sum() // Wait for all groups to finish and sum the results
            }

        } catch (e: Exception) {
            Timber.e(e, "$TAG: An error occurred during the scan process.")
        }
        Timber.i("$TAG: Scan complete. Imported a total of $totalImportedCount new files.")
        return@withContext totalImportedCount
    }

    private suspend fun findVideoForGroup(albumTitle: String, artist: String): HolodexVideoItem? {
        val organization = extractOrgFromArtist(artist)
        val coreTitle = extractCoreTitle(albumTitle)

        // Search for both the unique part of the title AND the artist name
        val searchRequest = VideoSearchRequest(
            target = listOf("stream", "clip"),
            conditions = listOf(
                SearchCondition(text = coreTitle),
                SearchCondition(text = artist)
            ),
            org = organization?.let { listOf(it) }
        )

        try {
            val searchResult = holodexRepository.holodexApiService.searchVideosAdvanced(searchRequest)

            val potentialVideos = searchResult.body()?.items
            if (potentialVideos.isNullOrEmpty()) {
                return null // API found no candidates
            }

            // Client-side filter: find the video in the results that is most similar to our full album title
            val bestVideoMatch = potentialVideos.maxByOrNull {
                calculateSimilarity(it.title, albumTitle)
            } ?: return null

            // Confidence check: If the best match is still not very similar, reject it.
            if (calculateSimilarity(bestVideoMatch.title, albumTitle) < 0.6) {
                Timber.w("$TAG: Found a potential video ('${bestVideoMatch.title}'), but it was not similar enough to '$albumTitle'.")
                return null
            }

            val videoWithSongsResult = holodexRepository.getVideoWithSongs(bestVideoMatch.id)
            return videoWithSongsResult.getOrNull()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: API error while finding video for group: $albumTitle")
            return null
        }
    }

    private fun findMatchingSong(fileTitle: String, apiSongs: List<HolodexSong>?): HolodexSong? {
        if (apiSongs.isNullOrEmpty()) return null

        val normalizedFileTitle = normalize(fileTitle)
        return apiSongs
            .map { apiSong ->
                val normalizedApiTitle = normalize(apiSong.name)
                val similarity = calculateSimilarity(normalizedFileTitle, normalizedApiTitle)
                apiSong to similarity
            }
            .filter { it.second >= SIMILARITY_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first
    }

    // FIX: Updated to use UnifiedDao with Metadata and Interaction tables
    private suspend fun importSong(
        fileInfo: FileInfo,
        video: HolodexVideoItem,
        song: HolodexSong
    ) {
        val itemId = "${video.id}_${song.start}"

        // 1. Upsert Metadata
        val metadata = UnifiedMetadataEntity(
            id = itemId,
            title = song.name,
            artistName = video.channel.name,
            type = "SEGMENT",
            specificArtUrl = song.artUrl ?: video.channel.photoUrl,
            uploaderAvatarUrl = video.channel.photoUrl,
            duration = (song.end - song.start).toLong(),
            channelId = video.channel.id ?: "unknown",
            parentVideoId = video.id,
            startSeconds = song.start.toLong(),
            endSeconds = song.end.toLong(),
            lastUpdatedAt = System.currentTimeMillis()
        )
        unifiedDao.upsertMetadata(metadata)

        // 2. Upsert Interaction (Download Status)
        val interaction = UserInteractionEntity(
            itemId = itemId,
            interactionType = "DOWNLOAD",
            timestamp = fileInfo.lastModified,
            localFilePath = fileInfo.uri.toString(),
            downloadStatus = DownloadStatus.COMPLETED.name,
            downloadFileName = fileInfo.name,
            downloadTargetFormat = "M4A",
            downloadProgress = 100,
            syncStatus = "SYNCED" // Legacy imports are local, but we mark them synced to avoid accidental deletion logic
        )
        unifiedDao.upsertInteraction(interaction)
    }

    private fun queryMediaStoreForAppDownloads(): List<FileInfo> {
        val files = mutableListOf<FileInfo>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%${File.separator}$DOWNLOAD_FOLDER_NAME${File.separator}%")

        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        try {
            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val contentUri = Uri.withAppendedPath(queryUri, id.toString())
                    files.add(FileInfo(contentUri, name, dateModified * 1000))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to query MediaStore.")
        }
        return files
    }

    private fun extractMetadata(uri: Uri): FileMetadata? {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

                if (title.isNullOrBlank() || artist.isNullOrBlank() || album.isNullOrBlank()) {
                    Timber.w("$TAG: File at uri '$uri' is missing essential metadata.")
                    null
                } else {
                    FileMetadata(title, artist, album)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to extract metadata from uri '$uri'")
            null
        }
    }

    private fun extractOrgFromArtist(artist: String): String? {
        return when {
            artist.contains("にじさんじ", ignoreCase = true) -> "Nijisanji"
            artist.contains("hololive", ignoreCase = true) -> "Hololive"
            else -> null
        }
    }

    private fun normalize(input: String): String {
        return input
            .lowercase()
            .replace(Regex("[\\s.,。/()!\\[\\]_{}\"']"), "")
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        val jaro = jaroDistance(s1, s2)
        if (jaro < 0.7) return jaro
        var prefix = 0
        for (i in 0 until minOf(s1.length, s2.length, 4)) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        return jaro + 0.1 * prefix * (1.0 - jaro)
    }

    private fun jaroDistance(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0
        val matchDistance = max(len1, len2) / 2 - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)
        var matches = 0
        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (!s2Matches[j] && s1[i] == s2[j]) {
                    s1Matches[i] = true
                    s2Matches[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0
        var t = 0.0
        var k = 0
        for (i in 0 until len1) {
            if (s1Matches[i]) {
                while (!s2Matches[k]) k++
                if (s1[i] != s2[k]) t++
                k++
            }
        }
        val transpositions = t / 2
        return (matches.toDouble() / len1 + matches.toDouble() / len2 + (matches - transpositions) / matches.toDouble()) / 3.0
    }

    private fun extractCoreTitle(albumTitle: String): String {
        // Priority 1: Extract content from Japanese brackets 【】
        val bracketRegex = Regex("【(.*?)】")
        val bracketMatch = bracketRegex.find(albumTitle)
        if (bracketMatch != null && bracketMatch.groupValues[1].isNotBlank()) {
            return bracketMatch.groupValues[1]
                .replace("#", "")
                .trim()
        }

        // Priority 2: Split by Japanese punctuation to find meaningful phrases
        val punctuationRegex = Regex("[、。]")
        val phrases = albumTitle.split(punctuationRegex)
            .map { it.trim() }
            .filter { it.length > 2 }

        if (phrases.isNotEmpty()) {
            return phrases.maxByOrNull { it.length } ?: albumTitle
        }

        // Fallback: Return full title if no punctuation found
        return albumTitle
    }
}