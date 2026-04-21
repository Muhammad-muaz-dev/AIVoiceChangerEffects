package com.example.aivoicechangersounds.ui.audioplayer


import android.media.MediaPlayer
import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AudioPlayerViewModel @Inject constructor() : ViewModel() {

    private val _playerState = MutableLiveData<PlayerState>(PlayerState.Idle)
    val playerState: LiveData<PlayerState> = _playerState

    private val _progress = MutableLiveData(0)
    val progress: LiveData<Int> = _progress

    private val _duration = MutableLiveData(0)
    val duration: LiveData<Int> = _duration

    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null

    fun initFromUrl(url: String) {
        _playerState.value = PlayerState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val player = MediaPlayer().apply {
                    setDataSource(url)
                    prepare()
                }
                withContext(Dispatchers.Main) {
                    setupMediaPlayer(player)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _playerState.value = PlayerState.Error("Failed to load audio: ${e.localizedMessage}")
                }
            }
        }
    }

    fun initFromBase64(base64: String, cacheDir: File) {
        _playerState.value = PlayerState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val file = File(cacheDir, "generated_audio_${System.currentTimeMillis()}.mp3")
                FileOutputStream(file).use { it.write(bytes) }
                tempFile = file

                val player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                }
                withContext(Dispatchers.Main) {
                    setupMediaPlayer(player)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _playerState.value = PlayerState.Error("Failed to decode audio: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun setupMediaPlayer(player: MediaPlayer) {
        mediaPlayer = player
        _duration.value = player.duration
        _playerState.value = PlayerState.Ready

        player.setOnCompletionListener {
            _playerState.value = PlayerState.Completed
            _progress.value = 0
        }
    }

    fun play() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                _playerState.value = PlayerState.Playing
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _playerState.value = PlayerState.Paused
            }
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _progress.value = position
    }

    fun updateProgress() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                _progress.value = player.currentPosition
            }
        }
    }

    fun replay() {
        mediaPlayer?.let { player ->
            player.seekTo(0)
            player.start()
            _playerState.value = PlayerState.Playing
            _progress.value = 0
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        tempFile?.delete()
    }

}

sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    object Ready : PlayerState()
    object Playing : PlayerState()
    object Paused : PlayerState()
    object Completed : PlayerState()
    data class Error(val message: String) : PlayerState()
}
