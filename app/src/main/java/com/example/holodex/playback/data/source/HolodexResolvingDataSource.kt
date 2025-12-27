package com.example.holodex.playback.data.source

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.data.repository.StreamUrlRepository // Changed Import
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class HolodexResolvingDataSource @Inject constructor(
    private val streamUrlRepository: StreamUrlRepository, // <-- INJECT THE NEW REPO
    private val unifiedDao: UnifiedDao
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri

        if (uri.scheme == "holodex" && uri.host == "resolve") {
            val rawId = uri.lastPathSegment ?: return dataSpec

            // 1. CHECK LOCAL DATABASE (Same as before - Pure Sync)
            val localPath = try {
                var interaction = unifiedDao.getDownloadInteractionSync(rawId)
                if (interaction == null && rawId.contains("_")) {
                    val parentId = rawId.substringBeforeLast("_")
                    interaction = unifiedDao.getDownloadInteractionSync(parentId)
                }

                if (interaction != null &&
                    interaction.downloadStatus == DownloadStatus.COMPLETED.name &&
                    !interaction.localFilePath.isNullOrBlank()
                ) {
                    val path = interaction.localFilePath!!
                    if (path.startsWith("content://") || File(path).exists()) path else null
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }

            if (localPath != null) {
                Timber.i("ResolvingDataSource: Using LOCAL file for $rawId")
                return dataSpec.buildUpon().setUri(Uri.parse(localPath)).build()
            }

            // 2. USE STORE5 CACHE (The Performance Fix)
            val videoIdForNetwork = if (rawId.contains("_") && rawId.substringAfterLast("_").all { it.isDigit() }) {
                rawId.substringBeforeLast("_")
            } else {
                rawId
            }

            Timber.i("ResolvingDataSource: Asking StreamUrlRepository for $videoIdForNetwork")

            val resolvedUrl = try {
                // runBlocking is required here by ExoPlayer interface.
                // Store5 handles the caching logic internally.
                runBlocking {
                    streamUrlRepository.getStreamUrl(videoIdForNetwork)
                }
            } catch (e: Exception) {
                Timber.e(e, "Resolution failed for $videoIdForNetwork")
                null
            }

            return if (resolvedUrl != null) {
                dataSpec.buildUpon().setUri(Uri.parse(resolvedUrl)).build()
            } else {
                dataSpec // Fail gracefully, let ExoPlayer handle error
            }
        }

        return dataSpec
    }
}