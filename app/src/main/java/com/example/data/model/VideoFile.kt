package com.example.data.model

data class VideoFile(
    val id: Long,
    val name: String,
    val path: String,
    val duration: Long,
    val size: Long,
    val resolution: String,
    val folderName: String,
    val thumbnailUri: String? = null
)
