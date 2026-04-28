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
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
@HiltViewModel
class VoiceEffectViewModel @Inject constructor(
    private val voiceEffectRepository: VoiceEffectRepository,  // for getVoices()
    private val voiceRepository: VoiceRepository               // for generateAudio() TTS call
) : ViewModel() {

    // ── Voices ────────────────────────────────────────────────────────────────

    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    val voices: StateFlow<List<Voice>> = _voices.asStateFlow()

    private val _voicesLoading = MutableStateFlow(false)
    val voicesLoading: StateFlow<Boolean> = _voicesLoading.asStateFlow()

    private val _voicesError = MutableStateFlow<String?>(null)
    val voicesError: StateFlow<String?> = _voicesError.asStateFlow()

    // ── Selected voice ────────────────────────────────────────────────────────

    private val _selectedVoice = MutableStateFlow<Voice?>(null)
    val selectedVoice: StateFlow<Voice?> = _selectedVoice.asStateFlow()

    // ── Audio playback (local recorded file) ──────────────────────────────────

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _totalDuration = MutableStateFlow(0)
    val totalDuration: StateFlow<Int> = _totalDuration.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    // ── Generate result ───────────────────────────────────────────────────────

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _generateResult = MutableStateFlow<GenerateAudioResponse?>(null)
    val generateResult: StateFlow<GenerateAudioResponse?> = _generateResult.asStateFlow()

    private val _generateError = MutableStateFlow<String?>(null)
    val generateError: StateFlow<String?> = _generateError.asStateFlow()

    // ── Internal state ────────────────────────────────────────────────────────

    private var mediaPlayer: MediaPlayer? = null
    private var audioFilePath: String? = null

    // Holds the STT text that arrived from RecordingActivity via Intent.
    // This is the ONLY source of text — no STT is done in this screen.
    private var transcribedText: String = ""

    fun setAudioFilePath(path: String) {
        audioFilePath = path
        prepareMediaPlayer(path)
    }

    fun setTranscribedText(text: String) {
        transcribedText = text.trim()
        Log.d("VoiceEffectViewModel", "Transcribed text set: '$transcribedText'")
    }

    // ── Voices ────────────────────────────────────────────────────────────────

    fun fetchVoices(language: String? = null) {
        viewModelScope.launch {
            _voicesLoading.value = true
            _voicesError.value = null
            when (val result = voiceEffectRepository.getVoices(language)) {
                is Resource.Success -> {
                    _voices.value = result.data
                    Log.d("VoiceEffectViewModel", "Voices loaded: ${result.data.size}")
                }
                is Resource.Error -> {
                    _voicesError.value = result.message
                    Log.e("VoiceEffectViewModel", "Voices error: ${result.message}")
                }
                else -> {}
            }
            _voicesLoading.value = false
        }
    }

    fun selectVoice(voice: Voice) {
        _selectedVoice.value = voice
        Log.d("VoiceEffectViewModel", "Voice selected: ${voice.name} id=${voice.id}")
    }

    fun generateVoiceEffect() {
        val voice = _selectedVoice.value
        if (voice == null) {
            _generateError.value = "Please select a voice first"
            return
        }

        val textToSend = transcribedText.trim()
        if (textToSend.isBlank()) {
            _generateError.value = "No speech detected. Please record again."
            Log.w("VoiceEffectViewModel", "Generate blocked: transcribed text is empty")
            return
        }

        Log.d("VoiceEffectViewModel", "Sending to backend → voiceId=${voice.id}, text='$textToSend'")

        viewModelScope.launch {
            _generating.value = true
            _generateError.value = null

            when (val result = voiceRepository.generateAudio(
                text = textToSend,
                model = voice.id,
                filePrefix = "tts"
            )) {
                is Resource.Success -> {
                    Log.d("VoiceEffectViewModel", "Generate success: ${result.data.audioUrl}")
                    _generateResult.value = result.data
                }
                is Resource.Error -> {
                    Log.e("VoiceEffectViewModel", "Generate error: ${result.message}")
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

    // ── MediaPlayer (local recorded audio playback) ───────────────────────────

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
                Log.d("VoiceEffectViewModel", "MediaPlayer ready — duration=${duration}ms")
            } catch (e: IOException) {
                Log.e("VoiceEffectViewModel", "MediaPlayer prepare failed: ${e.message}")
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