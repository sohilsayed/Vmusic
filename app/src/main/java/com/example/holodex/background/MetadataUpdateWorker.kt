// File: java/com/example/holodex/background/MetadataUpdateWorker.kt

package com.example.holodex.background

import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@HiltWorker
class MetadataUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        // Keys for WorkManager Data
        const val KEY_ITEM_ID = "ITEM_ID"
        const val KEY_FILE_URI = "FILE_URI"
        const val KEY_SONG_TITLE = "SONG_TITLE"
        const val KEY_ARTIST_NAME = "ARTIST_NAME"
        const val KEY_ALBUM_NAME = "ALBUM_NAME"
        const val KEY_ARTWORK_URI = "ARTWORK_URI"
        const val KEY_TRACK_NUMBER = "TRACK_NUMBER"
        private const val TAG = "MetadataUpdateWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return@withContext Result.failure()
        val fileUriString = inputData.getString(KEY_FILE_URI) ?: return@withContext Result.failure()
        val songTitle = inputData.getString(KEY_SONG_TITLE)
        val artistName = inputData.getString(KEY_ARTIST_NAME)
        val albumName = inputData.getString(KEY_ALBUM_NAME)
        val artworkUri = inputData.getString(KEY_ARTWORK_URI)
        val trackNumber = inputData.getInt(KEY_TRACK_NUMBER, -1)

        val tempFile = File(context.cacheDir, "metadata_update_${itemId}.m4a")

        try {
            val originalUri = fileUriString.toUri()
            Timber.d("$TAG: Starting metadata update for $itemId at URI: $originalUri")

            // 1. Copy original file to a temporary location for safe editing.
            context.contentResolver.openInputStream(originalUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Could not open input stream for original file.")

            // 2. Fetch new artwork if available.
            val artworkBytes = fetchArtwork(artworkUri)

            // 3. Use JAudioTagger to write new metadata to the temporary file.
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tagOrCreateAndSetDefault
            if (songTitle != null) tag.setField(FieldKey.TITLE, songTitle)
            if (artistName != null) tag.setField(FieldKey.ARTIST, artistName)
            if (albumName != null) tag.setField(FieldKey.ALBUM, albumName)
            if (trackNumber > 0) tag.setField(FieldKey.TRACK, trackNumber.toString())
            if (artworkBytes != null) {
                tag.deleteArtworkField()
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData = artworkBytes
                artwork.mimeType = "image/jpeg"
                tag.setField(artwork)
            }
            AudioFileIO.write(audioFile)
            Timber.d("$TAG: Successfully wrote new tags to temp file.")

            // 4. Overwrite the original file with the newly tagged temporary file.
            context.contentResolver.openOutputStream(originalUri, "w")?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Could not open output stream to overwrite original file.")

            Timber.i("$TAG: Successfully updated metadata for $itemId.")
            return@withContext Result.success()

        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to update metadata for $itemId.")
            return@withContext Result.failure()
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun fetchArtwork(url: String?): ByteArray? {
        if (url == null) return null
        val highResUrl = url.replace(Regex("""/100x100bb\.jpg$"""), "/1000x1000bb.jpg")
        return try {
            val request = ImageRequest.Builder(context).data(highResUrl).size(Size(1000, 1000))
                .allowHardware(false).build()
            (context.imageLoader.execute(request).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { bitmap ->
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch artwork from $highResUrl for metadata update.")
            null
        }
    }
}