package com.example.ui.files

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MusicTrack
import com.example.data.model.VideoFile
import com.example.ui.video.formatSize
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FileBrowserScreen(
    musicViewModel: MusicViewModel,
    videoViewModel: VideoViewModel,
    onVideoPlayNeeded: () -> Unit,
    onMusicPlayNeeded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val homeDirectory = remember {
        val root = Environment.getExternalStorageDirectory()
        if (root.exists() && root.canRead()) root else context.getExternalFilesDir(null) ?: File("/")
    }

    var currentPath by remember { mutableStateOf(homeDirectory) }
    var filesList by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Query folders on Dispatchers.IO
    fun loadDirectory(directory: File) {
        scope.launch {
            isLoading = true
            val list = withContext(Dispatchers.IO) {
                try {
                    if (directory.isDirectory) {
                        val children = directory.listFiles()
                        children?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                            ?: emptyList()
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
            filesList = list
            currentPath = directory
            isLoading = false
        }
    }

    LaunchedEffect(currentPath) {
        loadDirectory(currentPath)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("file_browser_screen")
    ) {
        // Headers with Navigation Actions Home / Up
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "متصفح الملفات (Files)",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currentPath.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Return Home button
                IconButton(
                    onClick = { loadDirectory(homeDirectory) },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .testTag("file_browser_home_btn")
                ) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = "Home Dir", tint = MaterialTheme.colorScheme.primary)
                }

                // Up directory folder level
                IconButton(
                    onClick = {
                        val parent = currentPath.parentFile
                        if (parent != null && parent.exists() && parent.canRead()) {
                            loadDirectory(parent)
                        }
                    },
                    enabled = currentPath.parentFile != null,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .testTag("file_browser_up_btn")
                ) {
                    Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Up Directory", tint = Color.White)
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (filesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "المجلد فارغ أو غير مصرح بالقراءة (Empty Directory)", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filesList) { file ->
                    FileBrowserRow(
                        file = file,
                        onClick = {
                            if (file.isDirectory) {
                                loadDirectory(file)
                            } else {
                                val mime = file.name.lowercase()
                                val isAudio = mime.endsWith(".mp3") || mime.endsWith(".wav") || mime.endsWith(".ogg") || mime.endsWith(".m4a")
                                val isVideo = mime.endsWith(".mp4") || mime.endsWith(".mkv") || mime.endsWith(".webm") || mime.endsWith(".3gp")

                                if (isAudio) {
                                    val track = MusicTrack(
                                        id = file.absolutePath.hashCode().toLong(),
                                        title = file.nameWithoutExtension,
                                        artist = "مستعرض محلي (Local Playback)",
                                        album = "تخزين الجهاز",
                                        duration = 0L,
                                        path = file.absolutePath
                                    )
                                    musicViewModel.playTrack(track)
                                    onMusicPlayNeeded()
                                } else if (isVideo) {
                                    val videoFile = VideoFile(
                                        id = file.absolutePath.hashCode().toLong(),
                                        name = file.nameWithoutExtension,
                                        path = file.absolutePath,
                                        duration = 0L,
                                        size = file.length(),
                                        resolution = "Unknown SD/HD",
                                        folderName = file.parentFile?.name ?: "Browse"
                                    )
                                    videoViewModel.selectVideo(videoFile)
                                    onVideoPlayNeeded()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FileBrowserRow(
    file: File,
    onClick: () -> Unit
) {
    val isDir = file.isDirectory
    val fileIcon = if (isDir) Icons.Default.Folder else {
        val ext = file.name.lowercase()
        if (ext.endsWith(".mp4") || ext.endsWith(".mkv") || ext.endsWith(".webm")) Icons.Default.Movie
        else if (ext.endsWith(".mp3") || ext.endsWith(".wav") || ext.endsWith(".ogg")) Icons.Default.MusicNote
        else Icons.Default.InsertDriveFile
    }
    val iconColor = if (isDir) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.7f)

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("file_browse_${file.name.hashCode()}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = fileIcon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (!isDir) {
                        Text(text = formatSize(file.length()), color = Color.LightGray.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    Text(text = formatDate(file.lastModified()), color = Color.LightGray.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
            if (isDir) {
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(date)
}
