package com.example.holodex.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataWriter @Inject constructor() {

    private val tag = "MetadataWriter"

    init {
        // Global configuration for JAudioTagger
        TagOptionSingleton.getInstance().isPadNumbers = false
    }

    suspend fun writeMetadata(
        context: Context,
        targetFile: File,
        itemId: String,
        songTitle: String,
        artistName: String,
        albumTitle: String,
        artworkUrl: String?,
        trackNumber: Int
    ) {
        try {
            Timber.d("$tag: Starting metadata tagging for ${targetFile.name}")
            val artworkData = fetchArtwork(context, artworkUrl)

            val audioFile = AudioFileIO.read(targetFile)
            val tag = audioFile.tagOrCreateAndSetDefault

            tag.setField(FieldKey.TITLE, songTitle)
            tag.setField(FieldKey.COMMENT, "holodex_item_id::$itemId")
            tag.setField(FieldKey.ARTIST, artistName)
            tag.setField(FieldKey.ALBUM, albumTitle)
            tag.setField(FieldKey.ALBUM_ARTIST, artistName)
            if (trackNumber > 0) {
                tag.setField(FieldKey.TRACK, trackNumber.toString())
            }

            if (artworkData != null) {
                tag.deleteArtworkField()
                val artwork = ArtworkFactory.getNew()
                artwork.binaryData = artworkData
                artwork.mimeType = "image/jpeg"
                artwork.description = "Cover"
                tag.setField(artwork)
            }
            AudioFileIO.write(audioFile)
            Timber.i("$tag: Successfully wrote tags to ${targetFile.name}")

        } catch (e: Exception) {
            throw IOException("JAudioTagger tagging failed.", e)
        }
    }

    private suspend fun fetchArtwork(context: Context, url: String?): ByteArray? {
        if (url == null) return null
        val highResUrl = url.replace(Regex("""/100x100bb\.jpg$"""), "/1000x1000bb.jpg")
        return try {
            val request = ImageRequest.Builder(context)
                .data(highResUrl)
                .size(Size(1000, 1000))
                .allowHardware(false)
                .build()
            (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap?.let { bitmap ->
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            Timber.e(e, "$tag: Failed to fetch artwork from $highResUrl")
            null
        }
    }
}