package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.Artist
import com.example.data.model.MusicTrack
import com.example.ui.files.FileBrowserScreen
import com.example.ui.music.ArtistProfileScreen
import com.example.ui.music.DashboardScreen
import com.example.ui.music.NowPlayingScreen
import com.example.ui.music.SongsListScreen
import com.example.ui.shared.MiniPlayer
import com.example.ui.shared.OnboardingScreen
import com.example.ui.shared.SettingsScreen
import com.example.ui.theme.PureMediaTheme
import com.example.ui.video.FoldersScreen
import com.example.ui.video.VideoListScreen
import com.example.ui.video.VideoPlayerScreen
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val musicViewModel: MusicViewModel by lazy {
        MusicViewModel(com.example.data.repository.MusicRepository(applicationContext), applicationContext)
    }
    private val videoViewModel: VideoViewModel by lazy {
        VideoViewModel(com.example.data.repository.VideoRepository(applicationContext), applicationContext)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PureMediaTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                // Dynamic permission gating
                var hasPermissions by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                        } else {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        }
                    )
                }

                // If user hasn't granted permissions, show the onboarding screen first
                if (!hasPermissions) {
                    OnboardingScreen(
                        onOnboardingComplete = {
                            hasPermissions = true
                            musicViewModel.loadMedia()
                            videoViewModel.loadFoldersAndVideos()
                        }
                    )
                } else {
                    // Navigation targets: "video", "music", "files", "settings"
                    var activeSection by remember { mutableStateOf("video") }
                    var musicSubSection by remember { mutableStateOf("dashboard") } // "dashboard" or "songs"
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                    // Sub-navigation selection helpers
                    var selectedArtistForProfile by remember { mutableStateOf<Artist?>(null) }
                    val selectedVideoInViewer by videoViewModel.selectedVideo.collectAsState()
                    val selectedFolderInViewer by videoViewModel.selectedFolder.collectAsState()

                    // Now playing bottom sheet expand state
                    var isNowPlayingExpanded by remember { mutableStateOf(false) }
                    val activeTrack by musicViewModel.currentTrack.collectAsState()

                    // Intercept back actions
                    BackHandler(enabled = true) {
                        when {
                            isNowPlayingExpanded -> {
                                isNowPlayingExpanded = false
                            }
                            selectedVideoInViewer != null -> {
                                videoViewModel.selectVideo(null)
                            }
                            selectedFolderInViewer != null -> {
                                videoViewModel.clearFolderSelection()
                            }
                            selectedArtistForProfile != null -> {
                                selectedArtistForProfile = null
                            }
                            musicSubSection == "songs" -> {
                                musicSubSection = "dashboard"
                            }
                            drawerState.isOpen -> {
                                scope.launch { drawerState.close() }
                            }
                            else -> {
                                // Default system back behavior
                                finish()
                            }
                        }
                    }

                    // Floating layout if playing a full-screen video
                    if (selectedVideoInViewer != null) {
                        VideoPlayerScreen(
                            videoViewModel = videoViewModel,
                            onBack = { videoViewModel.selectVideo(null) }
                        )
                    } else {
                        // Modal Navigation Drawer
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.width(280.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    // Custom visual drawer title
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicVideo,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "بيور ميديا بلاير",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = "PureMedia Player Hub",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Video Section Button
                                    NavigationDrawerItem(
                                        icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                                        label = { Text("مجلدات الفيديو (Videos)") },
                                        selected = activeSection == "video",
                                        onClick = {
                                            activeSection = "video"
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = Color.LightGray.copy(alpha = 0.6f),
                                            unselectedTextColor = Color.LightGray.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("drawer_nav_video")
                                    )

                                    // Music Section Button
                                    NavigationDrawerItem(
                                        icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                                        label = { Text("صالة الألحان (Music Studio)") },
                                        selected = activeSection == "music",
                                        onClick = {
                                            activeSection = "music"
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = Color.LightGray.copy(alpha = 0.6f),
                                            unselectedTextColor = Color.LightGray.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("drawer_nav_music")
                                    )

                                    // File Browser Button
                                    NavigationDrawerItem(
                                        icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                        label = { Text("متصفح الملفات (Local Storage)") },
                                        selected = activeSection == "files",
                                        onClick = {
                                            activeSection = "files"
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = Color.LightGray.copy(alpha = 0.6f),
                                            unselectedTextColor = Color.LightGray.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("drawer_nav_files")
                                    )

                                    // Settings Button
                                    NavigationDrawerItem(
                                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        label = { Text("إعدادات المشغل (Settings)") },
                                        selected = activeSection == "settings",
                                        onClick = {
                                            activeSection = "settings"
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = Color.LightGray.copy(alpha = 0.6f),
                                            unselectedTextColor = Color.LightGray.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("drawer_nav_settings")
                                    )
                                }
                            }
                        ) {
                            Scaffold(
                                topBar = {
                                    CenterAlignedTopAppBar(
                                        title = {
                                            Text(
                                                text = when (activeSection) {
                                                    "video" -> "بيورميديا فيديو (Cinema)"
                                                    "music" -> "بيورميديا للمقامات (Music)"
                                                    "files" -> "تخزين الجهاز (Files)"
                                                    else -> "إعدادات الأكولايزر والترجمة"
                                                },
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 17.sp,
                                                color = Color.White
                                            )
                                        },
                                        navigationIcon = {
                                            IconButton(
                                                onClick = { scope.launch { drawerState.open() } },
                                                modifier = Modifier.testTag("drawer_open_btn")
                                            ) {
                                                Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu Drawer", tint = Color.White)
                                            }
                                        },
                                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                            containerColor = MaterialTheme.colorScheme.background
                                        )
                                    )
                                },
                                bottomBar = {
                                    // MiniPlayer fits perfectly persistent over screens when media is loaded & not nowPlaying full screen
                                    if (activeTrack != null && !isNowPlayingExpanded) {
                                        MiniPlayer(
                                            musicViewModel = musicViewModel,
                                            onExpand = { isNowPlayingExpanded = true },
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                                .navigationBarsPadding()
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(innerPadding)
                                ) {
                                    when (activeSection) {
                                        "video" -> {
                                            if (selectedFolderInViewer == null) {
                                                FoldersScreen(
                                                    videoViewModel = videoViewModel,
                                                    onFolderClick = { name -> videoViewModel.selectFolder(name) }
                                                )
                                            } else {
                                                VideoListScreen(
                                                    videoViewModel = videoViewModel,
                                                    onBack = { videoViewModel.clearFolderSelection() },
                                                    onVideoClick = { file -> videoViewModel.selectVideo(file) }
                                                )
                                            }
                                        }
                                        "music" -> {
                                            if (selectedArtistForProfile != null) {
                                                ArtistProfileScreen(
                                                    musicViewModel = musicViewModel,
                                                    artist = selectedArtistForProfile!!,
                                                    onBack = { selectedArtistForProfile = null },
                                                    onTrackClick = { song -> musicViewModel.playTrack(song) }
                                                )
                                            } else if (musicSubSection == "songs") {
                                                SongsListScreen(
                                                    musicViewModel = musicViewModel,
                                                    onTrackClick = { song -> musicViewModel.playTrack(song) }
                                                )
                                            } else {
                                                DashboardScreen(
                                                    musicViewModel = musicViewModel,
                                                    onArtistClick = { artist -> selectedArtistForProfile = artist },
                                                    onTrackClick = { song -> musicViewModel.playTrack(song) },
                                                    onNavigateToSongs = { musicSubSection = "songs" }
                                                )
                                            }
                                        }
                                        "files" -> {
                                            FileBrowserScreen(
                                                musicViewModel = musicViewModel,
                                                videoViewModel = videoViewModel,
                                                onVideoPlayNeeded = {
                                                    // Trigger direct navigation route or let player overlay kick in
                                                },
                                                onMusicPlayNeeded = {
                                                    isNowPlayingExpanded = true
                                                }
                                            )
                                        }
                                        "settings" -> {
                                            SettingsScreen(
                                                musicViewModel = musicViewModel,
                                                videoViewModel = videoViewModel
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Animating the Expansion of NowPlaying Screen (Full Bleed Overlay)
                    AnimatedVisibility(
                        visible = isNowPlayingExpanded,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(stiffness = 300f)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(stiffness = 300f)
                        )
                    ) {
                        NowPlayingScreen(
                            musicViewModel = musicViewModel,
                            onCollapse = { isNowPlayingExpanded = false }
                        )
                    }
                }
            }
        }
    }
}
