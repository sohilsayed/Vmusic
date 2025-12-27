package com.example.holodex.playback.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class CoilBitmapLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader
) : BitmapLoader {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()

        scope.launch {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false) // Software bitmap required for Notifications
                    .build()

                val result = imageLoader.execute(request)
                val drawable = result.drawable

                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    future.set(drawable.bitmap)
                } else {
                    future.setException(IllegalArgumentException("Could not convert drawable to bitmap"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalArgumentException("Failed to decode byte array"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return true
    }
}