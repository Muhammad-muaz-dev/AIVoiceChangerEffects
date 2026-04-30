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
        private const val TAG = "VOICE_FLOW"
        private const val STT_WARMUP_DELAY_MS = 500L
        private const val STT_CAPTURE_WINDOW_MS = 3000L
        private const val RECORDING_CAPTURE_WINDOW_MS = 3000L
        private const val STT_FINALIZATION_MAX_WAIT_MS = 3000L
        private const val STT_FINALIZATION_STABLE_MS = 600L
        private const val STT_FINALIZATION_POLL_MS = 150L
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
    private var micHandoffJob: Job? = null
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
                Log.d(TAG, "startRecording() tapped")
                latestTranscribedText = ""
                _liveTranscribedText.value = ""
                _elapsedTime.value = 0

                currentFilePath = audioRecorderRepository.startRecording()
                Log.d(TAG, "Recorder started. path=$currentFilePath")
                _recordingState.value = RecordingState.Recording

                startTimer()
                startAmplitudeUpdates()
                startMicHandoffLoop(resetTranscript = true)

            } catch (e: Exception) {
                _recordingState.value =
                    RecordingState.Error(e.message ?: "Failed to start recording")
            }
        }
    }

    private fun pauseRecording() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "pauseRecording() tapped")
                stopMicHandoffLoop()
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
                Log.d(TAG, "resumeRecording() tapped")
                audioRecorderRepository.resumeRecording()
                _recordingState.value = RecordingState.Recording
                startTimer()
                startAmplitudeUpdates()
                startMicHandoffLoop(resetTranscript = false)
            } catch (e: Exception) {
                _recordingState.value =
                    RecordingState.Error(e.message ?: "Failed to resume recording")
            }
        }
    }

 fun onCancelClicked() {
        viewModelScope.launch {
            Log.d(TAG, "onCancelClicked()")
            // Cancelling means we don't care about the transcript — just tear
            // everything down immediately.
            stopMicHandoffLoop()
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
                Log.d(TAG, "onDoneClicked()")
                stopMicHandoffLoop()

                // Small delay to let the loop's finally/cancellation settle
                delay(200)

                val transcriptSnapshot = _liveTranscribedText.value.trim()
                val helperSnapshotBeforeStop = speechToTextHelper.getTranscriptSnapshot()

                val filePath = audioRecorderRepository.stopRecording()
                speechToTextHelper.stopListening()

                val finalText = waitForTranscriptToSettle(transcriptSnapshot)
                Log.d(
                    TAG,
                    "Done → filePath=$filePath, finalText='$finalText', " +
                        "uiSnapshot='$transcriptSnapshot', helperBeforeStop='$helperSnapshotBeforeStop', " +
                        "latest='$latestTranscribedText'"
                )
                speechToTextHelper.release()
                stopTimer()
                stopAmplitudeUpdates()
                _elapsedTime.value = 0
                if (filePath.isNullOrBlank()) {
                    Log.e(TAG, "onDoneClicked() failed: filePath is null/blank")
                    _recordingState.value = RecordingState.Error("Recorded file is missing. Please record again.")
                    return@launch
                }
                Log.d(TAG, "onDoneClicked() success: filePath=$filePath, finalTextLen=${finalText.length}")
                _recordingState.value = RecordingState.Done(
                    filePath = filePath,
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

    private fun startMicHandoffLoop(resetTranscript: Boolean) {
        micHandoffJob?.cancel()
        Log.d(TAG, "startMicHandoffLoop(resetTranscript=$resetTranscript)")
        micHandoffJob = viewModelScope.launch {
            if (!audioRecorderRepository.supportsPauseResume()) {
                Log.w(TAG, "Pause/resume not supported on this API level; running without live STT loop")
                return@launch
            }

            var resetAtStart = resetTranscript
            var cycle = 0
            while (_recordingState.value is RecordingState.Recording) {
                try {
                    cycle++
                    Log.d(TAG, "Mic handoff cycle #$cycle: pause recorder -> capture STT")
                    audioRecorderRepository.pauseRecording()
                    speechToTextHelper.startListening(resetTranscript = resetAtStart)
                    resetAtStart = false
                    delay(STT_WARMUP_DELAY_MS)
                    delay(STT_CAPTURE_WINDOW_MS)
                    speechToTextHelper.stopListening()
                    val settled = waitForTranscriptToSettle()
                    Log.d(TAG, "Mic handoff cycle #$cycle: STT settled len=${settled.length}")
                    speechToTextHelper.release()

                    if (!(_recordingState.value is RecordingState.Recording)) break
                    Log.d(TAG, "Mic handoff cycle #$cycle: resume recorder")
                    audioRecorderRepository.resumeRecording()
                    delay(RECORDING_CAPTURE_WINDOW_MS)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, "Mic handoff loop failed: ${e.message}", e)
                    _recordingState.value =
                        RecordingState.Error(e.message ?: "Failed during recording/transcription handoff")
                    break
                }
            }
        }
    }

    private fun stopMicHandoffLoop() {
        Log.d(TAG, "stopMicHandoffLoop()")
        micHandoffJob?.cancel()
        micHandoffJob = null
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
        stopMicHandoffLoop()
        speechToTextHelper.release()
        stopAmplitudeUpdates()
        viewModelScope.launch { audioRecorderRepository.cancelRecording() }
    }
}
