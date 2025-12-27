// File: java\com\example\holodex\background\M4AExportWorker.kt
package com.example.holodex.background

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.Transformer
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.holodex.data.db.DownloadStatus
import com.example.holodex.data.db.UnifiedDao
import com.example.holodex.di.DownloadCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
@HiltWorker
class M4AExportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    @DownloadCache private val downloadCache: SimpleCache,
    private val unifiedDao: UnifiedDao,
    private val metadataWriter: MetadataWriter // <-- INJECT the MetadataWriter
) : CoroutineWorker(context, workerParams) {

    companion object {
        // Keys for WorkManager Data
        const val KEY_ITEM_ID = "ITEM_ID"
        const val KEY_ORIGINAL_URI = "ORIGINAL_URI"
        const val KEY_SONG_TITLE = "SONG_TITLE"
        const val KEY_ARTIST_NAME = "ARTIST_NAME"
        const val KEY_ALBUM_NAME = "ALBUM_NAME"
        const val KEY_ARTWORK_URI = "ARTWORK_URI"
        const val KEY_CLIP_START_MS = "CLIP_START_MS"
        const val KEY_CLIP_END_MS = "CLIP_END_MS"
        const val KEY_TRACK_NUMBER = "TRACK_NUMBER"
        private const val TAG = "M4AExportWorker"
    }

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        val originalUriString = inputData.getString(KEY_ORIGINAL_URI) ?: return Result.failure()
        val songTitle = inputData.getString(KEY_SONG_TITLE) ?: "Unknown Title"
        val artistName = inputData.getString(KEY_ARTIST_NAME) ?: "Unknown Artist"
        val albumName = inputData.getString(KEY_ALBUM_NAME) ?: "Unknown Album"
        val artworkUri = inputData.getString(KEY_ARTWORK_URI)
        val clipStartMs = inputData.getLong(KEY_CLIP_START_MS, -1)
        val clipEndMs = inputData.getLong(KEY_CLIP_END_MS, -1)
        val trackNumber = inputData.getInt(KEY_TRACK_NUMBER, -1)

        if (clipStartMs == -1L || clipEndMs == -1L) {
            Timber.e("$TAG: Invalid clip times provided.")
            unifiedDao.updateDownloadStatus(itemId, DownloadStatus.FAILED.name)
            return Result.failure()
        }

        val decodedTitle = try {
            URLDecoder.decode(songTitle, "UTF-8")
        } catch (_: Exception) {
            songTitle
        }
        val sanitizedDisplayTitle = decodedTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
        val finalFileName = "$sanitizedDisplayTitle.m4a"

        val tempOutputFile = File(context.cacheDir, "transformer_output_${itemId}.m4a")

        Timber.d("$TAG: Starting export for item: $itemId, filename: $finalFileName")

        try {
            // Step 1: Use Transformer to create a clean, clipped M4A file
            val mediaItem = MediaItem.Builder()
                .setUri(originalUriString.toUri())
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clipStartMs)
                        .setEndPositionMs(clipEndMs)
                        .build()
                )
                .build()

            val exportResult = withContext(Dispatchers.Main) {
                val transformer = buildTransformerOnMainThread()
                transformMediaItem(transformer, mediaItem, tempOutputFile.absolutePath)
            }

            if (exportResult.exportException != null) throw exportResult.exportException!!
            Timber.d("$TAG: Transformer successfully created temp file: ${tempOutputFile.absolutePath}")

            // Step 2: Write metadata to the temporary file using the injected MetadataWriter
            metadataWriter.writeMetadata(
                context = context,
                targetFile = tempOutputFile,
                itemId = itemId,
                songTitle = songTitle,
                artistName = artistName,
                albumTitle = albumName,
                artworkUrl = artworkUri,
                trackNumber = trackNumber
            )
            Timber.d("$TAG: MetadataWriter successfully wrote tags to temp file.")


            // Step 3:  export the now-tagged file

            val finalUri = exportToMediaStore(tempOutputFile, finalFileName)
                ?: throw IOException("Failed to export temp file to MediaStore.")

            // Step 4: Finalize and clean up
            unifiedDao.completeDownload(itemId, finalUri.toString())
            downloadCache.removeResource(itemId)
            tempOutputFile.delete()

            Timber.i("$TAG: Successfully exported item $itemId to $finalUri")
            return Result.success()

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Export failed for item $itemId.")
            unifiedDao.updateDownloadStatus(itemId, DownloadStatus.FAILED.name)
            tempOutputFile.delete()
            return Result.failure()
        }
    }

    private fun buildTransformerOnMainThread(): Transformer {
        val upstreamDataSourceFactory = DefaultHttpDataSource.Factory()
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
        val mediaSourceFactory =
            DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory)
        val assetLoaderFactory = ExoPlayerAssetLoader.Factory(
            context,
            DefaultDecoderFactory.Builder(context).build(),
            Clock.DEFAULT,
            mediaSourceFactory
        )

        return Transformer.Builder(context)
            .setAssetLoaderFactory(assetLoaderFactory)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .experimentalSetMp4EditListTrimEnabled(true)
            .setMuxerFactory(InAppMp4Muxer.Factory())
            .build()
    }

    private suspend fun transformMediaItem(
        transformer: Transformer,
        mediaItem: MediaItem,
        outputPath: String
    ): ExportResult {
        return suspendCancellableCoroutine { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(exportResult)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }
            transformer.addListener(listener)
            continuation.invokeOnCancellation {
                transformer.removeListener(listener)
                transformer.cancel()
            }
            transformer.start(mediaItem, outputPath)
        }
    }



    private fun exportToMediaStore(sourceFile: File, finalFileName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_MUSIC + File.separator + "HolodexMusic"
                )
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val musicDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val appMusicDir = File(musicDir, "HolodexMusic")
                if (!appMusicDir.exists() && !appMusicDir.mkdirs()) {
                    return null
                }
                val targetFile = File(appMusicDir, finalFileName)
                put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, contentValues)
        uri?.let { outputUri ->
            try {
                resolver.openOutputStream(outputUri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(outputUri, contentValues, null, null)
                }
                return outputUri
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy file to MediaStore for $finalFileName.")
                resolver.delete(outputUri, null, null)
            }
        }
        return null
    }

}