package com.example.ui.video

import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.TypedValue
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.example.data.model.VideoFile
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoViewModel: VideoViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val video by videoViewModel.selectedVideo.collectAsState()

    if (video == null) return

    val currentVideo = video!!

    val subFontSize by videoViewModel.subFontSize.collectAsState()
    val subTextColor by videoViewModel.subTextColor.collectAsState()
    val subBgColor by videoViewModel.subBgColor.collectAsState()
    val defaultSpeed by videoViewModel.defaultSpeed.collectAsState()

    // ExoPlayer state
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var speedSelection by remember { mutableStateOf(defaultSpeed) }

    // Toggle menu displays
    var showControls by remember { mutableStateOf(true) }
    var showSubtitlePanel by remember { mutableStateOf(false) }

    // Restore saved play position on launch
    LaunchedEffect(currentVideo.path) {
        val savedPosition = videoViewModel.getVideoPlaybackPosition(currentVideo.path)
        if (savedPosition > 0L) {
            exoPlayer.seekTo(savedPosition)
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(currentVideo.path))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Capture states
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            videoViewModel.saveVideoPlaybackPosition(currentVideo.path, exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Auto progress updater
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPos = exoPlayer.currentPosition
            delay(500)
        }
    }

    // Hide controls after timeout
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_root")
    ) {
        // Player Surface View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    // Ensure the subtitle layout is ready
                    subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subFontSize)
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            subTextColor.toArgb(),
                            subBgColor.toArgb(),
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE,
                            AndroidColor.TRANSPARENT,
                            null
                        )
                    )
                }
            },
            update = { playerView ->
                playerView.subtitleView?.let { subView ->
                    subView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subFontSize)
                    subView.setStyle(
                        CaptionStyleCompat(
                            subTextColor.toArgb(),
                            subBgColor.toArgb(),
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE,
                            AndroidColor.TRANSPARENT,
                            null
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Click on empty space toggles controls
                    detectDragGestures(
                        onDrag = { _, _ -> },
                        onDragStart = { showControls = !showControls }
                    )
                }
        )

        // Custom Overlay Controls
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                videoViewModel.saveVideoPlaybackPosition(currentVideo.path, exoPlayer.currentPosition)
                                onBack()
                            },
                            modifier = Modifier.testTag("player_back_btn")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentVideo.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Subtitle Styling Button
                        IconButton(
                            onClick = { showSubtitlePanel = !showSubtitlePanel },
                            modifier = Modifier.testTag("player_subtitle_btn")
                        ) {
                            Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = MaterialTheme.colorScheme.primary)
                        }

                        // Playback Speed Selector Option
                        IconButton(
                            onClick = {
                                val nextSpeed = when (speedSelection) {
                                    "1.0x" -> "1.25x"
                                    "1.25x" -> "1.5x"
                                    "1.5x" -> "2.0x"
                                    else -> "1.0x"
                                }
                                speedSelection = nextSpeed
                                val param = nextSpeed.removeSuffix("x").toFloatOrNull() ?: 1.0f
                                exoPlayer.setPlaybackSpeed(param)
                            },
                            modifier = Modifier.testTag("player_speed_btn")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Text(text = speedSelection, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Middle controls
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.width(40.dp))
                    IconButton(
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag("player_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play / Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(40.dp))
                    IconButton(
                        onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(totalDuration)) },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Bottom Seek Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatDuration(currentPos), color = Color.White, fontSize = 12.sp)
                        Text(text = formatDuration(totalDuration), color = Color.White, fontSize = 12.sp)
                    }
                    Slider(
                        value = if (totalDuration > 0) currentPos.toFloat() / totalDuration else 0f,
                        onValueChange = { ratio ->
                            val seekTarget = (ratio * totalDuration).toLong()
                            exoPlayer.seekTo(seekTarget)
                            currentPos = seekTarget
                        },
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            thumbColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Subtitle customization panel drawer overlay sheet (Floating Card)
        if (showSubtitlePanel) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(310.dp)
                    .fillMaxHeight()
                    .padding(12.dp)
                    .testTag("subtitle_config_panel"),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تعديل الترجمة (Subtitles)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        IconButton(onClick = { showSubtitlePanel = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Size Slider
                    Text(
                        text = "حجم الخط (Font Size): ${subFontSize.toInt()}sp",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Slider(
                        value = subFontSize,
                        onValueChange = { videoViewModel.updateSubtitleFontSize(it) },
                        valueRange = 10f..32f,
                        colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.testTag("subtitle_size_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Color Wheel Label
                    Text(
                        text = "عجلة الألوان (Subtitle Hue Palette)",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Canvas-Based Real-Time Color Wheel
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ColorWheel(
                            onColorSelected = { color ->
                                // Drag selects text color, double tap changes background
                                videoViewModel.updateSubtitleTextColor(color)
                            },
                            onBgColorSelected = { color ->
                                videoViewModel.updateSubtitleBgColor(color.copy(alpha = 0.5f))
                            }
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { videoViewModel.updateSubtitleTextColor(Color.Yellow) }) {
                                Text("نص أصفر", color = Color.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { videoViewModel.updateSubtitleTextColor(Color.White) }) {
                                Text("نص أبيض", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A beautiful sweep-gradient Canvas Color Wheel that supports drag motions
 * to dynamically feed color values to the subtitles immediately.
 */
@Composable
fun ColorWheel(
    onColorSelected: (Color) -> Unit,
    onBgColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragPosition by remember { mutableStateOf(Offset(100f, 100f)) }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .padding(8.dp)
            .clip(CircleShape)
            .testTag("color_wheel_canvas_wrapper"),
        contentAlignment = Alignment.Center
    ) {
        val sizePx = constraints.maxWidth.toFloat()
        val radius = sizePx / 2f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(radius) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val currentX = change.position.x
                        val currentY = change.position.y

                        // Compute local angles relative to circle center
                        val dx = currentX - radius
                        val dy = currentY - radius
                        val distance = sqrt(dx * dx + dy * dy)

                        if (distance <= radius) {
                            // Convert polar back to HSV coordinates
                            val angleRad = atan2(dy, dx)
                            var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                            if (angleDeg < 0) angleDeg += 360f

                            val saturation = (distance / radius).coerceIn(0f, 1f)
                            val composeColor = Color.hsv(
                                hue = angleDeg,
                                saturation = saturation,
                                value = 1.0f
                            )

                            onColorSelected(composeColor)
                            // Background gets opposite complement color
                            val complementAngle = (angleDeg + 180f) % 360f
                            val compColor = Color.hsv(
                                hue = complementAngle,
                                saturation = 0.8f,
                                value = 0.2f
                            )
                            onBgColorSelected(compColor)
                        }
                    }
                }
        ) {
            // High fidelity spectrum paint
            val colors = listOf(
                Color.Red, Color.Yellow, Color.Green,
                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
            drawCircle(
                brush = Brush.sweepGradient(colors),
                radius = radius
            )
            // Overlay circular brightness wash
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    radius = radius
                ),
                radius = radius
            )
        }
    }
}

data class Offset(val x: Float, val y: Float)
