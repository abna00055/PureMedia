package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.Artist
import com.example.data.model.MusicTrack
import com.example.data.repository.MusicRepository
import com.example.service.MusicPlaybackService
import com.example.util.LrcLine
import com.example.util.LrcParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface MusicUiState {
    object Idle : MusicUiState
    object Loading : MusicUiState
    data class Success(val tracks: List<MusicTrack>) : MusicUiState
    data class Error(val message: String) : MusicUiState
}

class MusicViewModel(
    private val musicRepository: MusicRepository,
    private val context: Context
) : ViewModel() {

    var exoPlayer: ExoPlayer? = null
        private set

    // UI States
    private val _uiState = MutableStateFlow<MusicUiState>(MusicUiState.Idle)
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private val _allTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val allTracks: StateFlow<List<MusicTrack>> = _allTracks.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 200ms Search Debounce
    @OptIn(FlowPreview::class)
    val filteredTracks: StateFlow<List<MusicTrack>> = _searchQuery
        .debounce(200)
        .combine(_allTracks) { query, tracks ->
            if (query.isBlank()) {
                tracks
            } else {
                tracks.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favourites Persistence
    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    // Playback States
    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0L)
    val playbackProgress: StateFlow<Long> = _playbackProgress.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF) // 0=off, 1=one, 2=all
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Sycned Lyrics States
    private val _currentLyricsList = MutableStateFlow<List<LrcLine>>(emptyList())
    val currentLyricsList: StateFlow<List<LrcLine>> = _currentLyricsList.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(-1)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    // Equalizer Band values (visual state)
    val eq60Hz = MutableStateFlow(2f)
    val eq230Hz = MutableStateFlow(4f)
    val eq910Hz = MutableStateFlow(-1f)
    val eq3k6Hz = MutableStateFlow(3f)
    val eq14kHz = MutableStateFlow(6f)

    private var positionTimerJob: Job? = null

    init {
        activeInstance = this
        initializePlayer()
        loadMedia()
    }

    private fun initializePlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                        _isPlaying.value = isPlayingChanged
                        if (isPlayingChanged) {
                            startProgressTimer()
                        } else {
                            stopProgressTimer()
                            triggerServiceUpdate()
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            _playbackDuration.value = duration
                            triggerServiceUpdate()
                        } else if (state == Player.STATE_ENDED) {
                            nextTrack()
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val currentMediaId = mediaItem?.mediaId?.toLongOrNull()
                        if (currentMediaId != null) {
                            val track = _allTracks.value.find { it.id == currentMediaId }
                            if (track != null) {
                                _currentTrack.value = track
                                // Load synchronized lyrics
                                val rawLrc = musicRepository.getLyricsForTrack(track)
                                _currentLyricsList.value = LrcParser.parse(rawLrc)
                                triggerServiceUpdate()
                            }
                        }
                    }
                })
            }
        }
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.value = MusicUiState.Loading
            try {
                val songs = musicRepository.loadMusic()
                _allTracks.value = songs
                _uiState.value = MusicUiState.Success(songs)

                // Fill favorites initial state from flags
                val favIds = songs.filter { it.isFavorite }.map { it.id }.toSet()
                _favorites.value = favIds

                // Fetch artists profile list
                val artistList = musicRepository.getArtists()
                _artists.value = artistList
            } catch (e: Exception) {
                _uiState.value = MusicUiState.Error(e.localizedMessage ?: "Failed to scan files")
            }
        }
    }

    fun playTrack(track: MusicTrack) {
        initializePlayer()
        val player = exoPlayer ?: return

        _currentTrack.value = track
        val rawLrc = musicRepository.getLyricsForTrack(track)
        _currentLyricsList.value = LrcParser.parse(rawLrc)

        player.stop()
        player.clearMediaItems()

        // Setup playlists inside player
        val currentIndex = _allTracks.value.indexOfFirst { it.id == track.id }
        if (currentIndex != -1) {
            _allTracks.value.forEach { t ->
                player.addMediaItem(
                    MediaItem.Builder()
                        .setUri(t.path)
                        .setMediaId(t.id.toString())
                        .build()
                )
            }
            player.seekTo(currentIndex, 0L)
            player.prepare()
            player.play()
        } else {
            // Backup fallback single track play
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(track.path)
                    .setMediaId(track.id.toString())
                    .build()
            )
            player.prepare()
            player.play()
        }

        triggerServiceUpdate()
    }

    fun playPlayback() {
        exoPlayer?.play()
    }

    fun pausePlayback() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                // Play first track as fallback
                allTracks.value.firstOrNull()?.let { playTrack(it) }
            } else {
                player.play()
            }
        }
    }

    fun nextTrack() {
        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (_isShuffle.value) {
            // Pick random track
            val randomTrack = _allTracks.value.randomOrNull()
            if (randomTrack != null) playTrack(randomTrack)
        } else {
            // Loop back to start if repeat all is configured or simply play first
            player.seekTo(0, 0L)
        }
    }

    fun previousTrack() {
        val player = exoPlayer ?: return
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0, 0L) // restart
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _playbackProgress.value = positionMs
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        exoPlayer?.shuffleModeEnabled = _isShuffle.value
    }

    fun toggleFavorite(trackId: Long) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        _favorites.value = current

        // Update tracks favorite property inline
        _allTracks.value = _allTracks.value.map {
            if (it.id == trackId) {
                it.copy(isFavorite = current.contains(trackId))
            } else {
                it
            }
        }
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer?.repeatMode = _repeatMode.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Ticks progress every 200ms and syncs LRC lines
    private fun startProgressTimer() {
        stopProgressTimer()
        positionTimerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                val player = exoPlayer
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition
                    _playbackProgress.value = pos

                    // Perform binary search on lrc lines
                    val index = LrcParser.findCurrentLineIndex(_currentLyricsList.value, pos)
                    _activeLyricIndex.value = index
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTimer() {
        positionTimerJob?.cancel()
        positionTimerJob = null
    }

    private fun triggerServiceUpdate() {
        try {
            val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_PLAY // updates notify structure
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        if (activeInstance == this) {
            activeInstance = null
        }
        stopProgressTimer()
        exoPlayer?.release()
        exoPlayer = null
        super.onCleared()
    }

    companion object {
        @Volatile
        var activeInstance: MusicViewModel? = null
            private set
    }
}
