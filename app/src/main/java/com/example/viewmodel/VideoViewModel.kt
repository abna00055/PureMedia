package com.example.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.VideoFile
import com.example.data.model.VideoFolder
import com.example.data.repository.VideoRepository
import com.example.service.VideoPositionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoUiState {
    object Idle : VideoUiState
    object Loading : VideoUiState
    data class Success(val folders: List<VideoFolder>) : VideoUiState
    data class Error(val message: String) : VideoUiState
}

class VideoViewModel(
    private val videoRepository: VideoRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Idle)
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private val _allVideos = MutableStateFlow<List<VideoFile>>(emptyList())
    val allVideos: StateFlow<List<VideoFile>> = _allVideos.asStateFlow()

    private val _folders = MutableStateFlow<List<VideoFolder>>(emptyList())
    val folders: StateFlow<List<VideoFolder>> = _folders.asStateFlow()

    private val _selectedFolderVideos = MutableStateFlow<List<VideoFile>>(emptyList())
    val selectedFolderVideos: StateFlow<List<VideoFile>> = _selectedFolderVideos.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _selectedVideo = MutableStateFlow<VideoFile?>(null)
    val selectedVideo: StateFlow<VideoFile?> = _selectedVideo.asStateFlow()

    // Layout configuration models
    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    // SUBTITLE CUSTOM CONFIGURATIONS
    private val _subLanguage = MutableStateFlow("Arabic (AR)")
    val subLanguage: StateFlow<String> = _subLanguage.asStateFlow()

    private val _subFontSize = MutableStateFlow(16f)
    val subFontSize: StateFlow<Float> = _subFontSize.asStateFlow()

    private val _subTextColor = MutableStateFlow(Color.Yellow)
    val subTextColor: StateFlow<Color> = _subTextColor.asStateFlow()

    private val _subBgColor = MutableStateFlow(Color.Black.copy(alpha = 0.6f))
    val subBgColor: StateFlow<Color> = _subBgColor.asStateFlow()

    private val _rememberPosition = MutableStateFlow(true)
    val rememberPosition: StateFlow<Boolean> = _rememberPosition.asStateFlow()

    private val _defaultSpeed = MutableStateFlow("1.0x")
    val defaultSpeed: StateFlow<String> = _defaultSpeed.asStateFlow()

    init {
        loadFoldersAndVideos()
    }

    fun loadFoldersAndVideos() {
        viewModelScope.launch {
            _uiState.value = VideoUiState.Loading
            try {
                val videos = videoRepository.loadVideos()
                _allVideos.value = videos

                val folderList = videoRepository.loadFolders()
                _folders.value = folderList
                _uiState.value = VideoUiState.Success(folderList)
            } catch (e: Exception) {
                _uiState.value = VideoUiState.Error(e.localizedMessage ?: "Failed to list folder tree")
            }
        }
    }

    fun selectFolder(folderName: String) {
        _selectedFolder.value = folderName
        _selectedFolderVideos.value = _allVideos.value.filter { it.folderName == folderName }
    }

    fun clearFolderSelection() {
        _selectedFolder.value = null
        _selectedFolderVideos.value = emptyList()
    }

    fun selectVideo(video: VideoFile?) {
        _selectedVideo.value = video
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    // Dynamic Updates for settings
    fun updateSubtitleLanguage(lang: String) {
        _subLanguage.value = lang
    }

    fun updateSubtitleFontSize(size: Float) {
        _subFontSize.value = size
    }

    fun updateSubtitleTextColor(color: Color) {
        _subTextColor.value = color
    }

    fun updateSubtitleBgColor(color: Color) {
        _subBgColor.value = color
    }

    fun updateRememberPosition(remember: Boolean) {
        _rememberPosition.value = remember
    }

    fun updateDefaultSpeed(speed: String) {
        _defaultSpeed.value = speed
    }

    // Saved video position mapping
    fun saveVideoPlaybackPosition(videoPath: String, posMs: Long) {
        if (_rememberPosition.value) {
            viewModelScope.launch {
                try {
                    VideoPositionStore.savePosition(context, videoPath, posMs)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun getVideoPlaybackPosition(videoPath: String): Long {
        if (!_rememberPosition.value) return 0L
        return try {
            VideoPositionStore.getPosition(context, videoPath)
        } catch (e: Exception) {
            0L
        }
    }
}
