package com.example.aivoicechangersounds.Viewmodels

import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aivoicechangersounds.utils.AudioReverser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val audioReverser: AudioReverser
) : ViewModel() {

    companion object {
        private const val TAG = "PreviewViewModel"
    }

    // --- Playback state ---
    private val _isPlayingOriginal = MutableStateFlow(false)
    val isPlayingOriginal: StateFlow<Boolean> = _isPlayingOriginal.asStateFlow()

    private val _isPlayingReverse = MutableStateFlow(false)
    val isPlayingReverse: StateFlow<Boolean> = _isPlayingReverse.asStateFlow()

    private val _isReverseLoading = MutableStateFlow(false)
    val isReverseLoading: StateFlow<Boolean> = _isReverseLoading.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _totalDuration = MutableStateFlow(0)
    val totalDuration: StateFlow<Int> = _totalDuration.asStateFlow()

    private val _formattedCurrentTime = MutableStateFlow("00:00")
    val formattedCurrentTime: StateFlow<String> = _formattedCurrentTime.asStateFlow()

    private val _formattedTotalTime = MutableStateFlow("00:00")
    val formattedTotalTime: StateFlow<String> = _formattedTotalTime.asStateFlow()

    // --- File info ---
    private val _fileName = MutableStateFlow("")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _fileDate = MutableStateFlow("")
    val fileDate: StateFlow<String> = _fileDate.asStateFlow()

    // --- Save state ---
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    // --- Delete state ---
    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    // --- Rename state ---
    private val _renameSuccess = MutableStateFlow(false)
    val renameSuccess: StateFlow<Boolean> = _renameSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- MediaPlayer instances ---
    private var originalPlayer: MediaPlayer? = null
    private var reversePlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var audioFilePath: String? = null
    private var reversedFilePath: String? = null

    /**
     * Initialize with the audio file path received from ReverseVoice activity.
     * Prepares MediaPlayer and extracts file metadata.
     */
    fun initAudio(filePath: String) {
        audioFilePath = filePath
        val file = File(filePath)

        // Set file name and date
        _fileName.value = file.nameWithoutExtension
        val lastModified = java.text.SimpleDateFormat(
            "HH:mm | dd/MM/yyyy",
            java.util.Locale.getDefault()
        ).format(java.util.Date(file.lastModified()))
        _fileDate.value = lastModified

        prepareOriginalPlayer(filePath)

        // Pre-generate the reversed audio file in background
        generateReversedAudio(filePath)
    }

    private fun prepareOriginalPlayer(path: String) {
        releaseOriginalPlayer()
        originalPlayer = MediaPlayer().apply {
            try {
                setDataSource(path)
                prepare()
                _totalDuration.value = duration
                _formattedTotalTime.value = formatTime(duration)
                setOnCompletionListener {
                    _isPlayingOriginal.value = false
                    _currentPosition.value = 0
                    _formattedCurrentTime.value = "00:00"
                    stopProgressUpdates()
                }
            } catch (e: IOException) {
                Log.e(TAG, "prepareOriginalPlayer error: ${e.message}", e)
                _errorMessage.value = "Failed to load audio file"
            }
        }
    }

    /**
     * Uses AudioReverser to decode → reverse PCM samples → write WAV.
     * Runs on Dispatchers.IO via suspend function, never blocks UI.
     */
    private fun generateReversedAudio(inputPath: String) {
        viewModelScope.launch {
            _isReverseLoading.value = true
            try {
                val outputDir = File(inputPath).parentFile ?: return@launch
                val reversed = audioReverser.reverse(inputPath, outputDir)
                reversedFilePath = reversed
                prepareReversePlayer(reversed)
                Log.d(TAG, "Reversed audio generated: $reversed")
            } catch (e: Exception) {
                Log.e(TAG, "generateReversedAudio error: ${e.message}", e)
                _errorMessage.value = "Failed to generate reversed audio: ${e.message}"
            }
            _isReverseLoading.value = false
        }
    }

    private fun prepareReversePlayer(path: String) {
        releaseReversePlayer()
        reversePlayer = MediaPlayer().apply {
            try {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    _isPlayingReverse.value = false
                    _currentPosition.value = 0
                    _formattedCurrentTime.value = "00:00"
                    stopProgressUpdates()
                }
            } catch (e: IOException) {
                Log.e(TAG, "prepareReversePlayer error: ${e.message}", e)
                _errorMessage.value = "Failed to load reversed audio"
            }
        }
    }

    // --- Play Original ---
    fun onPlayOriginalClicked() {
        // Stop reverse if playing
        if (_isPlayingReverse.value) {
            stopReverse()
        }

        val player = originalPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _isPlayingOriginal.value = false
            stopProgressUpdates()
        } else {
            player.start()
            _isPlayingOriginal.value = true
            startProgressUpdates()
        }
    }

    // --- Play Reverse ---
    fun onPlayReverseClicked() {
        // If reversed audio is still being generated, show message
        if (_isReverseLoading.value) {
            _errorMessage.value = "Reversed audio is still being prepared, please wait..."
            return
        }

        // Stop original if playing
        if (_isPlayingOriginal.value) {
            stopOriginal()
        }

        val player = reversePlayer
        if (player == null) {
            _errorMessage.value = "Reversed audio not available"
            return
        }

        if (player.isPlaying) {
            player.pause()
            _isPlayingReverse.value = false
            stopProgressUpdates()
        } else {
            player.start()
            _isPlayingReverse.value = true
            startProgressUpdates()
        }
    }

    private fun stopOriginal() {
        originalPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
        }
        _isPlayingOriginal.value = false
        stopProgressUpdates()
    }

    private fun stopReverse() {
        reversePlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
        }
        _isPlayingReverse.value = false
        stopProgressUpdates()
    }

    // --- SeekBar ---
    fun seekTo(position: Int) {
        if (_isPlayingOriginal.value || !_isPlayingReverse.value) {
            originalPlayer?.seekTo(position)
        } else {
            reversePlayer?.seekTo(position)
        }
        _currentPosition.value = position
        _formattedCurrentTime.value = formatTime(position)
    }

    // --- Progress updates (coroutine, never blocks UI) ---
    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(200L)
                val activePlayer = when {
                    _isPlayingOriginal.value -> originalPlayer
                    _isPlayingReverse.value -> reversePlayer
                    else -> null
                }
                activePlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition
                        _currentPosition.value = pos
                        _formattedCurrentTime.value = formatTime(pos)
                    }
                }
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
    }

    // --- Save to files ---
    fun saveToFiles(destinationDir: File) {
        val sourcePath = audioFilePath ?: run {
            _errorMessage.value = "No audio file to save"
            return
        }

        viewModelScope.launch {
            try {
                val sourceFile = File(sourcePath)
                val destFile = File(destinationDir, sourceFile.name)
                sourceFile.copyTo(destFile, overwrite = true)
                _saveSuccess.value = true
            } catch (e: Exception) {
                Log.e(TAG, "saveToFiles error: ${e.message}", e)
                _errorMessage.value = "Failed to save file: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }

    // --- Delete audio file ---
    fun deleteAudioFile() {
        stopOriginal()
        stopReverse()
        releaseOriginalPlayer()
        releaseReversePlayer()

        viewModelScope.launch {
            try {
                audioFilePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
                reversedFilePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
                _deleteSuccess.value = true
            } catch (e: Exception) {
                Log.e(TAG, "deleteAudioFile error: ${e.message}", e)
                _errorMessage.value = "Failed to delete file: ${e.message}"
            }
        }
    }

    fun clearDeleteSuccess() {
        _deleteSuccess.value = false
    }

    // --- Rename audio file ---
    fun renameAudioFile(newName: String) {
        val currentPath = audioFilePath ?: run {
            _errorMessage.value = "No audio file to rename"
            return
        }

        viewModelScope.launch {
            try {
                stopOriginal()
                stopReverse()
                releaseOriginalPlayer()
                releaseReversePlayer()

                val currentFile = File(currentPath)
                val extension = currentFile.extension
                val newFile = File(currentFile.parentFile, "$newName.$extension")

                if (currentFile.renameTo(newFile)) {
                    audioFilePath = newFile.absolutePath
                    _fileName.value = newName
                    prepareOriginalPlayer(newFile.absolutePath)
                    generateReversedAudio(newFile.absolutePath)
                    _renameSuccess.value = true
                } else {
                    _errorMessage.value = "Failed to rename file"
                }
            } catch (e: Exception) {
                Log.e(TAG, "renameAudioFile error: ${e.message}", e)
                _errorMessage.value = "Failed to rename file: ${e.message}"
            }
        }
    }

    fun clearRenameSuccess() {
        _renameSuccess.value = false
    }

    /**
     * Returns the current audio file path for sharing.
     */
    fun getAudioFilePath(): String? = audioFilePath

    private fun formatTime(millis: Int): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun releaseOriginalPlayer() {
        originalPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        originalPlayer = null
    }

    private fun releaseReversePlayer() {
        reversePlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        reversePlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        releaseOriginalPlayer()
        releaseReversePlayer()
        // Clean up the reversed temp file
        reversedFilePath?.let { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete reversed file: ${e.message}")
            }
        }
    }
}
