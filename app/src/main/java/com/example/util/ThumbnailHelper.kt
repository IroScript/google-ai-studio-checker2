package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailHelper {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun getVideoThumbnail(context: Context, uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        memoryCache.get(uriString)?.let { return@withContext it }

        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            val uri = Uri.parse(uriString)
            if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                val headers = HashMap<String, String>()
                headers["User-Agent"] = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36 KidsTube/1.0"
                retriever.setDataSource(uriString, headers)
            } else if (uriString.startsWith("content://") || uriString.startsWith("android.resource://")) {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(uriString)
            }

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    320,
                    180
                )
            } else {
                retriever.getFrameAtTime(
                    1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            }

            if (bitmap != null) {
                memoryCache.put(uriString, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever?.release()
            } catch (ignored: Exception) {
            }
        }
    }
}

