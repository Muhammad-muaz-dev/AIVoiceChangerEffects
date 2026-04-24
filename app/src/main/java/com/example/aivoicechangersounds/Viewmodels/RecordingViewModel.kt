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
    private val audioRecorderRepository: AudioRecorderRepository,
    private val speechToTextHelper: SpeechToTextHelper
) : ViewModel() {

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    private val _formattedTime = MutableStateFlow("00:00")
    val formattedTime: StateFlow<String> = _formattedTime.asStateFlow()

    private var timerJob: Job? = null
    private var currentFilePath: String? = null

    fun onAudioButtonClicked() {
        when (_recordingState.value) {
            is RecordingState.Idle,
            is RecordingState.Cancelled -> startRecording()
            is RecordingState.Recording -> pauseRecording()
            is RecordingState.Paused -> resumeRecording()
            else -> { /* no-op */ }
        }
    }

    private fun startRecording() {
        viewModelScope.launch {
            try {
                speechToTextHelper.startListening()
                currentFilePath = audioRecorderRepository.startRecording()
                _recordingState.value = RecordingState.Recording
                startTimer()
            } catch (e: Exception) {
                speechToTextHelper.stopListening()
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
                _recordingState.value = RecordingState.Paused
                pauseTimer()
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
                _recordingState.value = RecordingState.Recording
                startTimer()
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(
                    e.message ?: "Failed to resume recording"
                )
            }
        }
    }

    fun onCancelClicked() {
        viewModelScope.launch {
            try {
                speechToTextHelper.stopListening()
                audioRecorderRepository.cancelRecording()
                stopTimer()
                _elapsedTime.value = 0L
                _formattedTime.value = "00:00"
                currentFilePath = null
                _recordingState.value = RecordingState.Cancelled
                // Reset to Idle so user can record again
                _recordingState.value = RecordingState.Idle
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(
                    e.message ?: "Failed to cancel recording"
                )
            }
        }
    }

    fun onDoneClicked() {
        viewModelScope.launch {
            try {
                val transcribedText = speechToTextHelper.stopListening()
                val filePath = audioRecorderRepository.stopRecording()
                stopTimer()
                if (filePath != null) {
                    _recordingState.value = RecordingState.Done(filePath, transcribedText)
                } else {
                    _recordingState.value = RecordingState.Error("Recording file not found")
                }
            } catch (e: Exception) {
                speechToTextHelper.stopListening()
                _recordingState.value = RecordingState.Error(
                    e.message ?: "Failed to stop recording"
                )
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _elapsedTime.value += 1
                _formattedTime.value = formatTime(_elapsedTime.value)
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
    }

    private fun stopTimer() {
        timerJob?.cancel()
    }

    private fun formatTime(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        speechToTextHelper.stopListening()
        viewModelScope.launch {
            try {
                audioRecorderRepository.cancelRecording()
            } catch (_: Exception) { }
        }
    }
}