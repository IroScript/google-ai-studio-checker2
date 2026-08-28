package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.LruCache
import android.util.Size
import com.example.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ThumbnailHelper {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 6).coerceAtLeast(2048)
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    private fun getDiskCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "kids_video_thumbnails")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun hashKey(key: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(key.toByteArray())
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            key.hashCode().toString()
        }
    }

    fun getMemoryCachedThumbnail(uriString: String): Bitmap? {
        return memoryCache.get(uriString)
    }

    suspend fun preloadThumbnails(context: Context, videos: List<VideoItem>) = withContext(Dispatchers.IO) {
        for (video in videos) {
            if (video.youtubeId == null && memoryCache.get(video.uriString) == null) {
                try {
                    getVideoThumbnail(context, video.uriString)
                } catch (ignored: Exception) {}
            }
        }
    }

    suspend fun getVideoThumbnail(context: Context, uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        // 1. Level 1: In-Memory LRU Cache (0ms Instant Access)
        memoryCache.get(uriString)?.let { return@withContext it }

        val hash = hashKey(uriString)
        val diskCacheDir = getDiskCacheDir(context)
        val cacheFile = File(diskCacheDir, "$hash.webp")

        // 2. Level 2: Persistent Disk Cache (1-2ms Access)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(uriString, bitmap)
                    return@withContext bitmap
                }
            } catch (ignored: Exception) {
                cacheFile.delete()
            }
        }

        val uri = Uri.parse(uriString)
        var bitmap: Bitmap? = null

        // 3. Level 3: Android OS Hardware Thumbnail Cache (Like MX Player - 0ms/fast)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (uriString.startsWith("content://") || uriString.startsWith("file://"))) {
                bitmap = context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && DocumentsContract.isDocumentUri(context, uri)) {
                bitmap = DocumentsContract.getDocumentThumbnail(context.contentResolver, uri, Point(320, 180), null)
            }
        } catch (ignored: Exception) {}

        // 4. Level 4: MediaMetadataRetriever Fallback
        if (bitmap == null) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                    val headers = HashMap<String, String>()
                    headers["User-Agent"] = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 KidsTube/1.0"
                    retriever.setDataSource(uriString, headers)
                } else if (uriString.startsWith("content://") || uriString.startsWith("android.resource://")) {
                    retriever.setDataSource(context, uri)
                } else {
                    retriever.setDataSource(uriString)
                }

                bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
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
            } catch (ignored: Exception) {
            } finally {
                try {
                    retriever?.release()
                } catch (ignored: Exception) {}
            }
        }

        // 5. Save in Memory and Write to Disk Cache
        if (bitmap != null) {
            memoryCache.put(uriString, bitmap)
            try {
                FileOutputStream(cacheFile).use { out ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                    } else {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                }
            } catch (ignored: Exception) {}
        }

        bitmap
    }
}

