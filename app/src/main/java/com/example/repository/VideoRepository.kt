package com.example.repository

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.model.VideoItem
import com.example.util.YoutubeIdHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class VideoRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("kids_tube_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_VIDEOS = "kids_videos_json"
        private const val KEY_FIRST_RUN = "kids_first_run_initialized"

        val SAMPLE_VIDEOS = listOf(
            VideoItem(
                id = "sample_1",
                title = "Big Buck Bunny - Forest Friends 🐰",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                folderName = "Kids Adventures",
                youtubeId = "aqz-KE-bpKQ",
                isSample = true,
                durationMs = 596000L
            ),
            VideoItem(
                id = "sample_2",
                title = "Elephant Dream - Fun Science 🐘",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                folderName = "Kids Adventures",
                youtubeId = "TLkA0RELQ1g",
                isSample = true,
                durationMs = 653000L
            ),
            VideoItem(
                id = "sample_3",
                title = "For Bigger Blazes - Animated Cars 🚗",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                folderName = "Fun Cartoons",
                youtubeId = "b9kgVz-eK5U",
                isSample = true,
                durationMs = 15000L
            ),
            VideoItem(
                id = "sample_4",
                title = "For Bigger Escapes - Nature Wonder 🌲",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                folderName = "Animals & Nature",
                youtubeId = "yA7CE35c5c0",
                isSample = true,
                durationMs = 15000L
            ),
            VideoItem(
                id = "sample_5",
                title = "For Bigger Joyrides - Roller Coaster 🎡",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                folderName = "Fun Cartoons",
                youtubeId = "60e-y2x0x5U",
                isSample = true,
                durationMs = 15000L
            ),
            VideoItem(
                id = "sample_6",
                title = "Sintel - The Dragon Quest 🐲",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                folderName = "Kids Adventures",
                youtubeId = "eRsGyueVLvQ",
                isSample = true,
                durationMs = 888000L
            ),
            VideoItem(
                id = "sample_7",
                title = "Tears of Steel - Robot Galaxy 🤖",
                uriString = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                folderName = "Kids Adventures",
                youtubeId = "R6MlUcmOul8",
                isSample = true,
                durationMs = 734000L
            )
        )
    }

    suspend fun getSavedVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString(KEY_VIDEOS, null)
        if (jsonString != null) {
            try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<VideoItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        VideoItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            uriString = obj.getString("uriString"),
                            folderName = obj.optString("folderName", "Default"),
                            youtubeId = if (obj.has("youtubeId")) obj.getString("youtubeId") else null,
                            isSample = obj.optBoolean("isSample", false),
                            durationMs = obj.optLong("durationMs", 0L)
                        )
                    )
                }
                return@withContext list
            } catch (e: Exception) {
                // fall through to default sample
            }
        }

        // If not initialized yet, seed with sample videos
        saveVideos(SAMPLE_VIDEOS)
        SAMPLE_VIDEOS
    }

    suspend fun saveVideos(videos: List<VideoItem>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        for (item in videos) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("uriString", item.uriString)
            obj.put("folderName", item.folderName)
            item.youtubeId?.let { obj.put("youtubeId", it) }
            obj.put("isSample", item.isSample)
            obj.put("durationMs", item.durationMs)
            array.put(obj)
        }
        prefs.edit().putString(KEY_VIDEOS, array.toString()).apply()
    }

    /**
     * Scan files from DocumentTree URI (Storage Access Framework) recursively across all subfolders
     */
    suspend fun scanDocumentTree(treeUri: Uri): List<VideoItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoItem>()
        val contentResolver: ContentResolver = context.contentResolver

        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootFolderName = getDocumentDisplayName(contentResolver, treeUri) ?: "Root Folder"

            fun scanDirectory(docId: String, currentFolderPath: String) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                val cursor: Cursor? = contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )

                cursor?.use {
                    val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (it.moveToNext()) {
                        val childId = it.getString(idIndex)
                        val displayName = it.getString(nameIndex) ?: continue
                        val mimeType = it.getString(mimeIndex)

                        val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR ||
                                mimeType == "vnd.android.document/directory"

                        if (isDir) {
                            // Recursively scan subfolder and sub-subfolder
                            val nextPath = if (currentFolderPath.isEmpty()) displayName else "$currentFolderPath / $displayName"
                            scanDirectory(childId, nextPath)
                        } else {
                            val isVideo = mimeType?.startsWith("video/") == true ||
                                    isVideoExtension(displayName)

                            if (isVideo) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                                val folderDisplay = if (currentFolderPath.isEmpty()) rootFolderName else currentFolderPath
                                results.add(
                                    VideoItem(
                                        id = UUID.randomUUID().toString(),
                                        title = cleanTitle(displayName),
                                        uriString = fileUri.toString(),
                                        folderName = folderDisplay,
                                        youtubeId = YoutubeIdHelper.extractYoutubeId(displayName),
                                        isSample = false
                                    )
                                )
                            }
                        }
                    }
                }
            }

            scanDirectory(rootDocId, "")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        results
    }

    suspend fun createVideoFromUri(uri: Uri, context: Context): VideoItem = withContext(Dispatchers.IO) {
        var displayName = "Video ${System.currentTimeMillis() % 1000}"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        displayName = it.getString(nameIdx)
                    }
                }
            }
        } catch (ignored: Exception) {}

        VideoItem(
            id = UUID.randomUUID().toString(),
            title = cleanTitle(displayName),
            uriString = uri.toString(),
            folderName = "Imported",
            youtubeId = YoutubeIdHelper.extractYoutubeId(displayName),
            isSample = false
        )
    }

    suspend fun scanDeviceVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val bucketCol = it.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Video $id"
                    val duration = it.getLong(durCol)
                    val bucketName = if (bucketCol >= 0) it.getString(bucketCol) ?: "Device" else "Device"
                    val contentUri = Uri.withAppendedPath(collection, id.toString())

                    results.add(
                        VideoItem(
                            id = "device_$id",
                            title = cleanTitle(name),
                            uriString = contentUri.toString(),
                            folderName = bucketName,
                            youtubeId = YoutubeIdHelper.extractYoutubeId(name),
                            isSample = false,
                            durationMs = duration
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        results
    }

    private fun getDocumentDisplayName(resolver: ContentResolver, uri: Uri): String? {
        try {
            val cursor = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (ignored: Exception) {}
        return null
    }

    private fun isVideoExtension(fileName: String?): Boolean {
        if (fileName == null) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("mp4", "mkv", "mov", "avi", "flv", "wmv", "webm", "ts", "m2ts", "3gp", "3g2", "m4v", "mpg", "mpeg", "vob", "ogv", "divx")
    }

    private fun cleanTitle(rawName: String): String {
        return rawName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
    }
}
