package com.example.aivoicechangersounds.Viewmodels

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aivoicechangersounds.data.models.FileCategory
import com.example.aivoicechangersounds.data.models.FileItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class FileViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "FileViewModel"
    }

    private val _allFiles = MutableStateFlow<List<FileItem>>(emptyList())

    private val _filteredFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val filteredFiles: StateFlow<List<FileItem>> = _filteredFiles.asStateFlow()

    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _playingFilePath = MutableStateFlow<String?>(null)
    val playingFilePath: StateFlow<String?> = _playingFilePath.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    fun loadFiles() {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { scanFiles() }
            _allFiles.value = files
            applyFilter(_selectedCategory.value)
        }
    }

    fun selectCategory(category: FileCategory) {
        _selectedCategory.value = category
        applyFilter(category)
    }

    private fun applyFilter(category: FileCategory) {
        _filteredFiles.value = if (category == FileCategory.ALL) {
            _allFiles.value
        } else {
            _allFiles.value.filter { it.category == category }
        }
    }

    private fun scanFiles(): List<FileItem> {
        val items = mutableListOf<FileItem>()

        val recordingsDir = File(context.filesDir, "recordings")
        if (recordingsDir.exists()) {
            recordingsDir.listFiles()?.forEach { file ->
                if (file.isFile && isAudioFile(file)) {
                    val category = categorizeFile(file.name)
                    val duration = getAudioDuration(file.absolutePath)
                    items.add(
                        FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            durationMs = duration,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            category = category
                        )
                    )
                }
            }
        }

        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && isAudioFile(file) &&
                (file.name.startsWith("tts_") || file.name.startsWith("generated_audio_") ||
                        file.name.startsWith("effect_") || file.name.startsWith("translate_"))
            ) {
                val category = categorizeFile(file.name)
                val duration = getAudioDuration(file.absolutePath)
                items.add(
                    FileItem(
                        name = file.name,
                        path = file.absolutePath,
                        durationMs = duration,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        category = category
                    )
                )
            }
        }

        items.sortByDescending { it.lastModified }
        return items
    }

    private fun categorizeFile(fileName: String): FileCategory {
        return when {
            fileName.startsWith("reversed_") -> FileCategory.REVERSE
            fileName.startsWith("effect_") -> FileCategory.EFFECT
            fileName.startsWith("tts_") || fileName.startsWith("generated_audio_") -> FileCategory.AI_VOICE
            fileName.startsWith("recording_") -> FileCategory.EFFECT
            fileName.startsWith("translate_") -> FileCategory.TRANSLATE
            else -> FileCategory.AI_VOICE
        }
    }

    private fun isAudioFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in listOf("mp3", "wav", "m4a", "aac", "ogg", "opus", "mp4")
    }

    private fun getAudioDuration(path: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Could not read duration for $path: ${e.message}")
            0L
        }
    }

    fun playOrPause(fileItem: FileItem) {
        if (_playingFilePath.value == fileItem.path) {
            val player = mediaPlayer
            if (player != null && player.isPlaying) {
                player.pause()
                _playingFilePath.value = null
            } else {
                player?.start()
                _playingFilePath.value = fileItem.path
            }
            return
        }

        releasePlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(fileItem.path)
                prepare()
                start()
                setOnCompletionListener {
                    _playingFilePath.value = null
                }
            }
            _playingFilePath.value = fileItem.path
        } catch (e: IOException) {
            Log.e(TAG, "Error playing file: ${e.message}", e)
            _playingFilePath.value = null
        }
    }

    fun deleteFile(fileItem: FileItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (_playingFilePath.value == fileItem.path) {
                    releasePlayer()
                }
                val file = File(fileItem.path)
                if (file.exists()) file.delete()
            }
            loadFiles()
        }
    }

    fun renameFile(fileItem: FileItem, newName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val oldFile = File(fileItem.path)
                val ext = oldFile.extension
                val sanitizedName = newName.replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                val newFile = File(oldFile.parent, "$sanitizedName.$ext")
                if (oldFile.exists()) {
                    oldFile.renameTo(newFile)
                }
            }
            loadFiles()
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing player: ${e.message}")
        }
        mediaPlayer = null
        _playingFilePath.value = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
