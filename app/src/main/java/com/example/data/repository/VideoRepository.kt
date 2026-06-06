package com.example.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.example.data.model.VideoFile
import com.example.data.model.VideoFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(
    private val context: Context
) {
    private val mockVideos = listOf(
        VideoFile(
            id = 1001L,
            name = "Big Buck Bunny (Trailer)",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            duration = 596000L,
            size = 158 * 1024 * 1024L,
            resolution = "1280x720",
            folderName = "Cine Sample",
            thumbnailUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg"
        ),
        VideoFile(
            id = 1002L,
            name = "Sintel (Fantasy Open Movie)",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            duration = 888000L,
            size = 230 * 1024 * 1024L,
            resolution = "1920x1080",
            folderName = "Cine Sample",
            thumbnailUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg"
        ),
        VideoFile(
            id = 1003L,
            name = "Tears of Steel (VFX Demo)",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            duration = 734000L,
            size = 195 * 1024 * 1024L,
            resolution = "1920x1080",
            folderName = "VFX Reel",
            thumbnailUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg"
        ),
        VideoFile(
            id = 1004L,
            name = "For Bigger Blazes (Action)",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            duration = 15000L,
            size = 12 * 1024 * 1024L,
            resolution = "1280x720",
            folderName = "Promo Clips",
            thumbnailUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg"
        )
    )

    suspend fun loadVideos(): List<VideoFile> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<VideoFile>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        try {
            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            context.contentResolver.query(
                collectionUri,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val resCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)
                val folderCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val path = cursor.getString(dataCol) ?: ""
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val resolution = cursor.getString(resCol) ?: "Unknown"
                    val folderName = cursor.getString(folderCol) ?: "Root"

                    val videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    resultList.add(
                        VideoFile(
                            id = id,
                            name = name,
                            path = path,
                            duration = duration,
                            size = size,
                            resolution = resolution,
                            folderName = folderName,
                            thumbnailUri = videoUri.toString()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Merge with samples or return samples if empty (for direct testing inside simulators)
        if (resultList.isEmpty()) {
            mockVideos
        } else {
            resultList
        }
    }

    suspend fun loadFolders(): List<VideoFolder> {
        val videos = loadVideos()
        return videos.groupBy { it.folderName }
            .map { (folderName, list) ->
                VideoFolder(
                    name = folderName,
                    videoCount = list.size,
                    firstVideoThumbnail = list.firstOrNull()?.thumbnailUri
                )
            }
    }
}
