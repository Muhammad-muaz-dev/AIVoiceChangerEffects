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

        // Time we give SpeechRecognizer to fully grab the mic before MediaRecorder starts.
        private const val STT_WARMUP_DELAY_MS = 350L

        // Max time we'll wait for SpeechRecognizer's final onResults to land
        // in the transcript flow after asking it to stop.
        private const val STT_FINALIZATION_MAX_WAIT_MS = 2500L

        // We consider the transcript "settled" when it hasn't changed for this long.
        private const val STT_FINALIZATION_STABLE_MS = 500L

        // Polling interval while we're waiting for the transcript to settle.
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
        when (_recordingState.value) {
            is RecordingState.Idle,
            is RecordingState.Cancelled -> startRecording()
            is RecordingState.Recording -> pauseRecording()
            is RecordingState.Paused    -> resumeRecording()
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

                // 1. STT first
                speechToTextHelper.startListening(resetTranscript = true)

                // 2. Warm-up
                delay(STT_WARMUP_DELAY_MS)

                // 3. RE-ENABLE THIS LINE:
                currentFilePath = audioRecorderRepository.startRecording()

                _recordingState.value = RecordingState.Recording
                startTimer()

                // 4. RE-ENABLE THIS LINE TOO (it needs the recorder running):
                startAmplitudeUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "startRecording failed: ${e.message}", e)
                speechToTextHelper.stopListening()
                speechToTextHelper.release()
                _recordingState.value =
                    RecordingState.Error(e.message ?: "Failed to start recording")
            }
        }
    }

    private fun pauseRecording() {
        viewModelScope.launch {
            try {
                audioRecorderRepository.pauseRecording()

                // Ask STT to finalize the current segment, give it a moment to
                // commit the result to the transcript flow, then release.
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
                // 1. Stop MediaRecorder FIRST so the mic is released. STT
                //    can now fully finalize without competing for audio.
                val filePath = audioRecorderRepository.stopRecording()

                // 2. Ask SpeechRecognizer to finalize. This triggers its
                //    final onResults callback (asynchronously). Critically,
                //    stopListening() does NOT cancel/destroy the recognizer
                //    — that would discard the pending result.
                speechToTextHelper.stopListening()

                // 3. Wait for the final onResults to update the transcript
                //    flow. We poll until the value stops changing (or until
                //    we hit the max wait).
                val finalText = waitForTranscriptToSettle()

                // 4. Now safe to actually destroy the recognizer.
                speechToTextHelper.release()

                Log.d(TAG, "Done → filePath=$filePath, finalText='$finalText'")

                stopTimer()
                stopAmplitudeUpdates()
                _elapsedTime.value = 0

                // Your backend flow needs ONLY text + selected voice.
                // If the audio file failed to save (null/empty), do not block STT navigation.
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
    private suspend fun waitForTranscriptToSettle(): String {
        val start = System.currentTimeMillis()
        var lastValue = speechToTextHelper.transcribedText.value
        var lastChange = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < STT_FINALIZATION_MAX_WAIT_MS) {
            delay(STT_FINALIZATION_POLL_MS)

            val current = speechToTextHelper.transcribedText.value
            if (current != lastValue) {
                lastValue = current
                lastChange = System.currentTimeMillis()
                continue
            }

            // Value hasn't changed; if it's non-blank and stable, we're done.
            if (current.isNotBlank() &&
                System.currentTimeMillis() - lastChange >= STT_FINALIZATION_STABLE_MS
            ) {
                return current
            }
        }

        val final = speechToTextHelper.transcribedText.value
        return when {
            final.isNotBlank() -> final
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
