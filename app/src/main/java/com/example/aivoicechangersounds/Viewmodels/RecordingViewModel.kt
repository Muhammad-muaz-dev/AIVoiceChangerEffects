package com.example.aivoicechangersounds.Viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aivoicechangersounds.data.models.RecordingState
import com.example.aivoicechangersounds.data.repository.AudioRecorderRepository
import com.example.aivoicechangersounds.utils.SpeechToTextHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val speechToTextHelper: SpeechToTextHelper,
    private val audioRecorderRepository: AudioRecorderRepository
) : ViewModel() {

    private val _liveAmplitude = MutableStateFlow(0)
    val liveAmplitude: StateFlow<Int> = _liveAmplitude.asStateFlow()

    private val _recordingState =
        MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _elapsedTime = MutableStateFlow(0L)
    val formattedTime: StateFlow<String> = _elapsedTime
        .asStateFlow()
        .let { flow ->
            MutableStateFlow("00:00").apply {
                viewModelScope.launch {
                    flow.collect { value ->
                        this@apply.value = formatTime(value)
                    }
                }
            }
        }

    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var currentFilePath: String? = null

    fun onAudioButtonClicked() {
        when (_recordingState.value) {
            is RecordingState.Idle,
            is RecordingState.Cancelled -> startRecording()
            is RecordingState.Recording -> pauseRecording()
            is RecordingState.Paused -> resumeRecording()
            else -> {}
        }
    }

    private fun startRecording() {
        viewModelScope.launch {
            try {
                currentFilePath = audioRecorderRepository.startRecording()
                speechToTextHelper.startListening(resetTranscript = true)
                _recordingState.value = RecordingState.Recording
                startTimer()
                startAmplitudeUpdates()
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(
                    e.message ?: "Failed to start recording"
                )
            }
        }
    }

    private fun pauseRecording() {
        viewModelScope.launch {
            try {
                audioRecorderRepository.pauseRecording()
                speechToTextHelper.stopListening()
                _recordingState.value = RecordingState.Paused
                timerJob?.cancel()
                stopAmplitudeUpdates()
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(
                    e.message ?: "Failed to pause recording"
                )
            }
        }
    }

    private fun resumeRecording() {
        viewModelScope.launch {
            try {
                audioRecorderRepository.resumeRecording()
                speechToTextHelper.startListening(resetTranscript = false)
                _recordingState.value = RecordingState.Recording
                startTimer()
                startAmplitudeUpdates()
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(
                    e.message ?: "Failed to resume recording"
                )
            }
        }
    }

    fun onCancelClicked() {
        viewModelScope.launch {
            speechToTextHelper.stopListening()
            audioRecorderRepository.cancelRecording()
            stopTimer()
            stopAmplitudeUpdates()
            _elapsedTime.value = 0
            currentFilePath = null
            _recordingState.value = RecordingState.Idle
        }
    }

    fun onDoneClicked() {
        viewModelScope.launch {
            try {
                val text = speechToTextHelper.stopListening()
                val filePath = audioRecorderRepository.stopRecording()
                stopTimer()
                stopAmplitudeUpdates()
                _elapsedTime.value = 0
                if (filePath.isNullOrBlank()) {
                    _recordingState.value = RecordingState.Error("Recording file not found")
                } else {
                    _recordingState.value = RecordingState.Done(filePath, text)
                }
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(
                    e.message ?: "Failed to finish recording"
                )
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _elapsedTime.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
    }

    private fun startAmplitudeUpdates() {
        amplitudeJob?.cancel()
        amplitudeJob = viewModelScope.launch {
            while (_recordingState.value is RecordingState.Recording) {
                _liveAmplitude.value = audioRecorderRepository.getMaxAmplitude()
                delay(80)
            }
        }
    }

    private fun stopAmplitudeUpdates() {
        amplitudeJob?.cancel()
        _liveAmplitude.value = 0
    }

    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    override fun onCleared() {
        super.onCleared()
        speechToTextHelper.stopListening()
        stopAmplitudeUpdates()
        viewModelScope.launch {
            audioRecorderRepository.cancelRecording()
        }
    }
}