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
        private const val STT_WARMUP_DELAY_MS = 350L
        private const val STT_FINALIZATION_MAX_WAIT_MS = 2500L
        private const val STT_FINALIZATION_STABLE_MS = 500L
        private const val STT_FINALIZATION_POLL_MS = 100L
    }

    // ── State ────────────────────────────────────────────────────────────────

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

    private val _liveTranscribedText = MutableStateFlow("")
    val liveTranscribedText: StateFlow<String> = _liveTranscribedText.asStateFlow()

    // Latest non-blank text seen from STT — used as a fallback if the final
    // onResults never lands in time.
    private var latestTranscribedText: String = ""

    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var currentFilePath: String? = null

    init {
        viewModelScope.launch {
            speechToTextHelper.transcribedText.collect { text ->
                _liveTranscribedText.value = text
                if (text.isNotBlank()) latestTranscribedText = text
            }
        }
    }

    // ── Button handler ───────────────────────────────────────────────────────


    fun onAudioButtonClicked() {
        Log.d("processing error","function called")
        when (_recordingState.value) {
            is RecordingState.Idle,
            is RecordingState.start     ->startRecording()
            is RecordingState.Recording -> pauseRecording()
            is RecordingState.Paused,
            is RecordingState.Resume    -> resumeRecording()
            else -> Unit
        }
    }

    // ── Recording lifecycle ──────────────────────────────────────────────────

    private fun startRecording() {
        viewModelScope.launch {
            try {
                latestTranscribedText = ""
                _liveTranscribedText.value = ""
                _elapsedTime.value = 0

                // 1. START AUDIO FIRST (important)
                currentFilePath = audioRecorderRepository.startRecording()

                // 2. START STT IMMEDIATELY AFTER
                speechToTextHelper.startListening(resetTranscript = true)

                // 3. UPDATE STATE
                _recordingState.value = RecordingState.Recording

                startTimer()
                startAmplitudeUpdates()

            } catch (e: Exception) {
                _recordingState.value =
                    RecordingState.Error(e.message ?: "Failed to start recording")
            }
        }
    }

    private fun pauseRecording() {
        viewModelScope.launch {
            try {
                audioRecorderRepository.pauseRecording()
                speechToTextHelper.stopListening()
                waitForTranscriptToSettle()
                speechToTextHelper.release()

                _recordingState.value = RecordingState.Paused
                timerJob?.cancel()
                stopAmplitudeUpdates()
            } catch (e: Exception) {
                _recordingState.value =
                    RecordingState.Error(e.message ?: "Failed to pause recording")
            }
        }
    }

    private fun resumeRecording() {
        viewModelScope.launch {
            try {
                Log.d("hello","this ois calling in ")
                // STT first again, then a tiny delay, then resume MediaRecorder.
                // resetTranscript = false keeps the accumulated text.
                speechToTextHelper.startListening(resetTranscript = false)
                delay(STT_WARMUP_DELAY_MS)
                audioRecorderRepository.resumeRecording()

                _recordingState.value = RecordingState.Recording
                startTimer()
                startAmplitudeUpdates()
            } catch (e: Exception) {
                _recordingState.value =
                    RecordingState.Error(e.message ?: "Failed to resume recording")
            }
        }
    }

 fun onCancelClicked() {
        viewModelScope.launch {
            // Cancelling means we don't care about the transcript — just tear
            // everything down immediately.
            speechToTextHelper.release()
            audioRecorderRepository.cancelRecording()
            stopTimer()
            stopAmplitudeUpdates()
            _elapsedTime.value = 0
            _liveTranscribedText.value = ""
            latestTranscribedText = ""
            currentFilePath = null
            _recordingState.value = RecordingState.Idle
        }
    }

    fun onDoneClicked() {

        viewModelScope.launch {
            try {
                val transcriptSnapshot = _liveTranscribedText.value.trim()
                val helperSnapshotBeforeStop = speechToTextHelper.getTranscriptSnapshot()

                val filePath = audioRecorderRepository.stopRecording()
                Log.d("function called","Function is calling ")
                speechToTextHelper.stopListening()
                val finalText = waitForTranscriptToSettle(transcriptSnapshot)
                Log.d(
                    "finalresultbytranscribedtext",
                    "Done → filePath=$filePath, finalText='$finalText', " +
                        "uiSnapshot='$transcriptSnapshot', helperBeforeStop='$helperSnapshotBeforeStop', " +
                        "latest='$latestTranscribedText'"
                )
                speechToTextHelper.release()
                stopTimer()
                stopAmplitudeUpdates()
                _elapsedTime.value = 0
                _recordingState.value = RecordingState.Done(
                    filePath = filePath.orEmpty(),
                    transcribedText = finalText
                )
            } catch (e: Exception) {
                Log.e(TAG, "onDoneClicked failed: ${e.message}", e)
                speechToTextHelper.release()
                _recordingState.value =
                    RecordingState.Error(e.message ?: "Failed to finish recording")
            }
        }
    }
    private suspend fun waitForTranscriptToSettle(transcriptSnapshot: String = ""): String {
        val start = System.currentTimeMillis()
        var lastValue = speechToTextHelper.getTranscriptSnapshot()
        Log.d("pasttext","$lastValue")
        var lastChange = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < STT_FINALIZATION_MAX_WAIT_MS) {
            delay(STT_FINALIZATION_POLL_MS)

            val current = speechToTextHelper.getTranscriptSnapshot()
            Log.d("pasttext", "waitForTranscriptToSettle: $current")
            if (current != lastValue) {
                lastValue = current
                lastChange = System.currentTimeMillis()
                continue
            }
            if (current.isNotBlank() &&
                System.currentTimeMillis() - lastChange >= STT_FINALIZATION_STABLE_MS
            ) {
                return current
            }
        }

        val final = speechToTextHelper.getTranscriptSnapshot()
        val live = _liveTranscribedText.value.trim()
        Log.d("pasttext","the final text is $final")
        return when {
            final.isNotBlank() -> final
            live.isNotBlank() -> live
            transcriptSnapshot.isNotBlank() -> transcriptSnapshot
            latestTranscribedText.isNotBlank() -> latestTranscribedText
            else -> ""
        }
    }

    // ── Timer ────────────────────────────────────────────────────────────────

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

    // ── Amplitude ────────────────────────────────────────────────────────────

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
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s)
    }

    override fun onCleared() {
        super.onCleared()
        speechToTextHelper.release()
        stopAmplitudeUpdates()
        viewModelScope.launch { audioRecorderRepository.cancelRecording() }
    }
}
