package com.example.aivoicechangersounds.Viewmodels

import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aivoicechangersounds.data.models.GenerateAudioResponse
import com.example.aivoicechangersounds.data.models.Voice
import com.example.aivoicechangersounds.data.repository.VoiceEffectRepository
import com.example.aivoicechangersounds.data.repository.VoiceRepository
import com.example.aivoicechangersounds.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.File


@HiltViewModel
class VoiceEffectViewModel @Inject constructor(
    private val voiceEffectRepository: VoiceEffectRepository,
    private val voiceRepository: VoiceRepository
) : ViewModel() {

    // --- Voice list state ---
    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices: StateFlow<List<Voice>> = _voices.asStateFlow()

    private val _voicesLoading = MutableStateFlow(false)
    val voicesLoading: StateFlow<Boolean> = _voicesLoading.asStateFlow()

    private val _voicesError = MutableStateFlow<String?>(null)
    val voicesError: StateFlow<String?> = _voicesError.asStateFlow()

    // --- Selected voice ---
    private val _selectedVoice = MutableStateFlow<Voice?>(null)
    val selectedVoice: StateFlow<Voice?> = _selectedVoice.asStateFlow()

    // --- Audio playback state ---
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _totalDuration = MutableStateFlow(0)
    val totalDuration: StateFlow<Int> = _totalDuration.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    // --- Generate voice state ---
    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _generateResult = MutableStateFlow<GenerateAudioResponse?>(null)
    val generateResult: StateFlow<GenerateAudioResponse?> = _generateResult.asStateFlow()

    private val _generateError = MutableStateFlow<String?>(null)
    val generateError: StateFlow<String?> = _generateError.asStateFlow()

    // --- MediaPlayer ---
    private var mediaPlayer: MediaPlayer? = null
    private var audioFilePath: String? = null
    private var transcribedText: String? = null

    fun setAudioFilePath(path: String) {
        audioFilePath = path
        prepareMediaPlayer(path)
    }

    fun setTranscribedText(text: String) {
        transcribedText = text
    }

    private fun prepareMediaPlayer(path: String) {
        releaseMediaPlayer()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(path)
                prepare()
                _totalDuration.value = duration
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = 0
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun playPauseAudio() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.start()
            _isPlaying.value = true
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
    }

    fun updateProgress() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                _currentPosition.value = player.currentPosition
            }
        }
    }

    fun setVolume(volumeLevel: Float) {
        val clamped = volumeLevel.coerceIn(0f, 1f)
        _volume.value = clamped
        mediaPlayer?.setVolume(clamped, clamped)
    }

    fun increaseVolume() {
        setVolume((_volume.value + 0.1f).coerceAtMost(1f))
    }

    fun decreaseVolume() {
        setVolume((_volume.value - 0.1f).coerceAtLeast(0f))
    }

    // --- Voice list ---
    fun fetchVoices(language: String? = null) {
        viewModelScope.launch {
            _voicesLoading.value = true
            _voicesError.value = null

            val result = voiceEffectRepository.getVoices(language)

            when (result) {
                is Resource.Success -> {
                    _voices.value = result.data
                    Log.d(
                        "VoiceEffectViewModelinhg",
                        "fetchVoices Error: ${result.data}"
                    )
                }

                is Resource.Error -> {
                    _voicesError.value = result.message
                    Log.d(
                        "VoiceEffectViewModelinhg",
                        "fetchVoices Error: ${result.message}"
                    )
                }

                else -> {}
            }

            _voicesLoading.value = false


        }
    }

    fun selectVoice(voice: Voice) {
        _selectedVoice.value = voice
    }

    // --- Generate voice effect via TTS (audio → text → API) ---
    fun generateVoiceEffect() {
        val voice = _selectedVoice.value ?: return
        val path = audioFilePath
        val text = transcribedText?.trim()
        Log.d("Debugging  Option","voiceId='${voice.id}', filePath='$path', text='$text'")

        viewModelScope.launch {
            _generating.value = true
            _generateError.value = null

            val result = if (!path.isNullOrBlank() && File(path).exists()) {
                // Primary flow for VoiceEffect screen: upload recorded audio + selected voice
                when (val audioResult = voiceEffectRepository.generateVoice(voice.id, path)) {
                    is Resource.Success -> {
                        Resource.Success(
                            GenerateAudioResponse(
                                audioUrl = audioResult.data.audioUrl,
                                audioBase64 = audioResult.data.audioBase64,
                                filePath = null
                            )
                        )
                    }
                    is Resource.Error -> Resource.Error(audioResult.message)
                    else -> Resource.Error("Failed to generate voice effect")
                }
            } else if (!text.isNullOrBlank()) {
                // Fallback only when recorded file is unavailable
                voiceRepository.generateAudio(text, voice.id, "effect")
            } else {
                Resource.Error("Recorded audio not found. Please record again.")
            }

            when (result) {
                is Resource.Success -> {
                    _generateResult.value = result.data
                }

                is Resource.Error -> {
                    _generateError.value = result.message
                }

                else -> {}
            }

            _generating.value = false
        }
    }

    fun clearGenerateResult() {
        _generateResult.value = null
        _generateError.value = null
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0
        _totalDuration.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        releaseMediaPlayer()
    }
}