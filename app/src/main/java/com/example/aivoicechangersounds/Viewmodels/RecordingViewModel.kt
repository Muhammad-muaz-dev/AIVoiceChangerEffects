package com.example.aivoicechangersounds.Viewmodels

import android.util.Log
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

    companion object {
        private const val TAG = "RecordingViewModel"
        // Delay after stopping MediaRecorder so SpeechRecognizer can deliver final results
        private const val STT_FINALIZATION_DELAY_MS = 600L
        // Delay before starting MediaRecorder so SpeechRecognizer gets the mic first
        private const val MIC_INIT_DELAY_MS = 400L
    }

    private val _liveAmplitude = MutableStateFlow(0)
    val liveAmplitude: StateFlow<Int> = _liveAmplitude.asStateFlow()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _elapsedTime = MutableStateFlow(0L)
    val formattedTime: StateFlow<String> = _elapsedTime
        .asStateFlow()
        .let { flow ->
            MutableStateFlow("00:00").apply {
                viewModelScope.launch {
                    flow.collect { value -> this@apply.value = formatTime(value) }
                }
            }
        }

    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var currentFilePath: String? = null

    // Keeps the latest STT text so if stopListening() fires before onResults,
    // we still have the last known good value
    private var latestTranscribedText: String = ""

    init {
        viewModelScope.launch {
            speechToTextHelper.transcribedText.collect { text ->
                if (text.isNotBlank()) latestTranscribedText = text
            }
        }
    }

    // ── Button handler ────────────────────────────────────────────────────────

    fun onAudioButtonClicked() {
        when (_recordingState.value) {
            is RecordingState.Idle,
            is RecordingState.Cancelled -> startRecording()
            is RecordingState.Recording -> pauseRecording()
            is RecordingState.Paused    -> resumeRecording()
            else -> {}
        }
    }

    // ── Recording lifecycle ───────────────────────────────────────────────────

    private fun startRecording() {
        viewModelScope.launch {
            try {
                latestTranscribedText = ""
                _elapsedTime.value = 0

                // Start STT FIRST so SpeechRecognizer acquires the mic before MediaRecorder.
                // This avoids the mic-conflict that causes STT to silently fail on many devices.
                speechToTextHelper.startListening(resetTranscript = true)

                // Give STT time to initialise its audio capture
                delay(MIC_INIT_DELAY_MS)

                // Now start audio recording
                currentFilePath = audioRecorderRepository.startRecording()

                _recordingState.value = RecordingState.Recording
                startTimer()
                startAmplitudeUpdates()
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(e.message ?: "Failed to start recording")
            }
        }
    }

    private fun pauseRecording() {
        viewModelScope.launch {
            try {
                audioRecorderRepository.pauseRecording()
                // Stop STT on pause — partial text is committed inside stopListening()
                speechToTextHelper.stopListening()
                _recordingState.value = RecordingState.Paused
                timerJob?.cancel()
                stopAmplitudeUpdates()
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(e.message ?: "Failed to pause recording")
            }
        }
    }

    private fun resumeRecording() {
        viewModelScope.launch {
            try {
                audioRecorderRepository.resumeRecording()
                // resetTranscript = false so STT APPENDS to the existing text (not overwrites)
                speechToTextHelper.startListening(resetTranscript = false)
                _recordingState.value = RecordingState.Recording
                startTimer()
                startAmplitudeUpdates()
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(e.message ?: "Failed to resume recording")
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
            latestTranscribedText = ""
            currentFilePath = null
            _recordingState.value = RecordingState.Idle
        }
    }

    fun onDoneClicked() {
        viewModelScope.launch {
            try {
                // 1. Stop audio recording FIRST so the mic is released back to SpeechRecognizer.
                //    This lets STT deliver any pending final results that were blocked by
                //    the MediaRecorder holding the mic.
                val filePath = audioRecorderRepository.stopRecording()

                // 2. Give the SpeechRecognizer time to fire its final onResults callback
                //    now that the mic is free.
                delay(STT_FINALIZATION_DELAY_MS)

                // 3. stopListening() commits any partial text and returns the full transcript
                val textFromStop = speechToTextHelper.stopListening().trim()

                // 4. Fallback: if stopListening() returned empty, use what we collected in init{}
                val finalText = textFromStop.ifBlank { latestTranscribedText.trim() }

                Log.d(TAG, "Done → filePath=$filePath, finalText='$finalText'")

                stopTimer()
                stopAmplitudeUpdates()
                _elapsedTime.value = 0

                if (filePath.isNullOrBlank()) {
                    _recordingState.value = RecordingState.Error("Recording file not found")
                } else {
                    _recordingState.value = RecordingState.Done(
                        filePath = filePath,
                        transcribedText = finalText
                    )
                }
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error(e.message ?: "Failed to finish recording")
            }
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

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

    // ── Amplitude ─────────────────────────────────────────────────────────────

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
        viewModelScope.launch { audioRecorderRepository.cancelRecording() }
    }
}