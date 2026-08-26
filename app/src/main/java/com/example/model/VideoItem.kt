package com.example.model

data class VideoItem(
    val id: String,
    val title: String,
    val uriString: String,
    val folderName: String = "All Videos",
    val youtubeId: String? = null,
    val isSample: Boolean = false,
    val durationMs: Long = 0L
)
