package com.example.ui.music

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.model.MusicTrack
import com.example.ui.video.formatDuration
import com.example.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    musicViewModel: MusicViewModel,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentTrack by musicViewModel.currentTrack.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val progress by musicViewModel.playbackProgress.collectAsState()
    val duration by musicViewModel.playbackDuration.collectAsState()
    val isShuffle by musicViewModel.isShuffle.collectAsState()
    val repeatMode by musicViewModel.repeatMode.collectAsState()

    val lyricsLines by musicViewModel.currentLyricsList.collectAsState()
    val activeLyricIndex by musicViewModel.activeLyricIndex.collectAsState()

    var showLyricsView by remember { mutableStateOf(false) }
    var speedSelection by remember { mutableStateOf("1.0x") }

    // DYNAMIC BACKGROUND COLOR PALETTE EXTRACTION
    var dominantColor by remember { mutableStateOf(Color(0xFF180D35)) } // Amethyst base Dark default
    val animatedColor by animateColorAsState(targetValue = dominantColor, animationSpec = spring())

    LaunchedEffect(currentTrack) {
        val track = currentTrack ?: return@LaunchedEffect
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(track.albumArtUri ?: "https://picsum.photos/id/101/600/600")
                .allowHardware(false) // Required for palette bitmap reading
                .build()

            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                // Generate Palette on backdrop
                Palette.from(bitmap).generate { palette ->
                    palette?.dominantSwatch?.rgb?.let { rgbColor ->
                        dominantColor = Color(rgbColor).copy(alpha = 0.5f)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Centered scrolling helper for lyrics
    val lyricsListState = rememberLazyListState()
    LaunchedEffect(activeLyricIndex) {
        if (showLyricsView && activeLyricIndex >= 0 && activeLyricIndex < lyricsLines.size) {
            // Animates to center index with comfortable offsets
            lyricsListState.animateScrollToItem(activeLyricIndex, scrollOffset = -220)
        }
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            animatedColor,
            Color(0xFF040209) // Deep night void contrast bottom
        )
    )

    if (currentTrack == null) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush)
            .padding(24.dp)
            .testTag("now_playing_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("now_playing_collapse_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse screen",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "تشغيل النغمات (Now Playing)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                IconButton(
                    onClick = { showLyricsView = !showLyricsView },
                    modifier = Modifier.testTag("now_playing_lyrics_toggle")
                ) {
                    Icon(
                        imageVector = if (showLyricsView) Icons.Default.MusicNote else Icons.Default.Receipt,
                        contentDescription = "Toggle Lyrics",
                        tint = if (showLyricsView) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }

            // MIDDLE CARD TRANSITION (Album Art vs Centered Synced Lyrics)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = showLyricsView, animationSpec = spring()) { displayLyrics ->
                    if (displayLyrics) {
                        // Synced Scrolling Lyrics List Layout
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("lyrics_list_container"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "الكلمات المتزامنة (Synced Lyrics)",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            if (lyricsLines.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "لا توجد كلمات متزامنة متاحة لهذا الملف (No LRC)",
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = lyricsListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(lyricsLines) { index, line ->
                                        val isActive = index == activeLyricIndex
                                        Text(
                                            text = line.text,
                                            color = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                            fontSize = if (isActive) 18.sp else 14.sp,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp)
                                                .scale(if (isActive) 1.05f else 1.0f),
                                            lineHeight = 26.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Beautiful Circular Album Art View Cards
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .testTag("album_art_circle_wrapper"),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = currentTrack?.albumArtUri ?: "https://picsum.photos/id/101/600/600",
                                    contentDescription = "Now Playing Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(244.dp)
                                        .clip(CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = currentTrack?.title ?: "",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentTrack?.artist ?: "",
                                color = Color.LightGray.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Seekbar progress container
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = formatDuration(progress), color = Color.LightGray.copy(alpha = 0.6f), fontSize = 11.sp)
                    IconButton(
                        onClick = {
                            val nextSpeedNum = when (speedSelection) {
                                "1.0x" -> "1.25x"
                                "1.25x" -> "1.5x"
                                "1.5x" -> "2.0x"
                                else -> "1.0x"
                            }
                            speedSelection = nextSpeedNum
                            val v = nextSpeedNum.removeSuffix("x").toFloatOrNull() ?: 1.0f
                            musicViewModel.exoPlayer?.setPlaybackSpeed(v)
                        },
                        modifier = Modifier.testTag("now_playing_speed_toggle")
                    ) {
                        Text(text = speedSelection, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(text = formatDuration(duration), color = Color.LightGray.copy(alpha = 0.6f), fontSize = 11.sp)
                }

                Slider(
                    value = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { ratio ->
                        val targetMs = (ratio * duration).toLong()
                        musicViewModel.seekTo(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        thumbColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("now_playing_seek_slider")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback controls panel row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle mode
                IconButton(
                    onClick = { musicViewModel.toggleShuffle() },
                    modifier = Modifier.testTag("now_playing_shuffle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = { musicViewModel.previousTrack() },
                    modifier = Modifier.testTag("now_playing_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Master Toggle play/pause
                IconButton(
                    onClick = { musicViewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(68.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("now_playing_play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = { musicViewModel.nextTrack() },
                    modifier = Modifier.testTag("now_playing_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Loop cycling repeat modes
                IconButton(
                    onClick = { musicViewModel.cycleRepeatMode() },
                    modifier = Modifier.testTag("now_playing_repeat_btn")
                ) {
                    val icon = when (repeatMode) {
                        1 -> Icons.Default.RepeatOne
                        2 -> Icons.Default.Repeat
                        else -> Icons.Default.Repeat
                    }
                    val isLight = repeatMode != 0
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat state",
                        tint = if (isLight) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
