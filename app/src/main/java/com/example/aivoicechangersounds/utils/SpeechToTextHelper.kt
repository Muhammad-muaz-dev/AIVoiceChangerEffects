package com.example.aivoicechangersounds.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class SpeechToTextHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val transcriptLock = Any()

    companion object {
        private const val TAG = "SpeechToTextHelper"

        private fun errorName(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT      -> "NETWORK_TIMEOUT(1)"
            SpeechRecognizer.ERROR_NETWORK              -> "NETWORK(2)"
            SpeechRecognizer.ERROR_AUDIO                -> "AUDIO(3)"
            SpeechRecognizer.ERROR_SERVER               -> "SERVER(4)"
            SpeechRecognizer.ERROR_CLIENT               -> "CLIENT(5)"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT       -> "SPEECH_TIMEOUT(6)"
            SpeechRecognizer.ERROR_NO_MATCH             -> "NO_MATCH(7)"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY      -> "RECOGNIZER_BUSY(8)"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS(9)"
            else -> "UNKNOWN($error)"
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var sttIntent: Intent? = null

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText

    private val accumulatedText = StringBuilder()

    @Volatile
    private var isActive = false

    // Diagnostic counters — reset on each startListening(reset=true) session.
    @Volatile private var partialCount = 0
    @Volatile private var resultCount = 0
    @Volatile private var beganSpeech = false

    // -----------------------------
    // START LISTENING
    // -----------------------------
    fun startListening(resetTranscript: Boolean = true) {
        mainHandler.post {
            if (resetTranscript) {
                synchronized(transcriptLock) { accumulatedText.setLength(0) }
                _transcribedText.value = ""
                partialCount = 0
                resultCount = 0
                beganSpeech = false
            }
            isActive = true
            Log.d(TAG, "startListening(resetTranscript=$resetTranscript)")
            createAndStart()
        }
    }

    // -----------------------------
    // STOP LISTENING (finalize)
    //
    // Only stops the audio capture so the engine flushes its final result via
    // onResults. Does NOT cancel/destroy — that would discard the result.
    // Call [release] afterwards once the final transcript has been collected.
    // -----------------------------
    fun stopListening() {
        isActive = false
        mainHandler.post {
            Log.d(
                "countsinstt",
                "stopListening() — partials=$partialCount results=$resultCount " +
                        "beganSpeech=$beganSpeech currentText='${_transcribedText.value}'"
            )
            try {
                val currentText = _transcribedText.value.trim()
                if (currentText.isNotBlank() && synchronized(transcriptLock) { accumulatedText.isEmpty() }) {
                    synchronized(transcriptLock) { accumulatedText.append(currentText) }
                    _transcribedText.value = accumulatedText.toString()
                    Log.d(TAG, "stopListening() preserved partial as final='$currentText'")
                }
                // Triggers the final onResults / onError callback.
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer: ${e.message}")
            }
        }
    }

    fun release() {
        isActive = false
        mainHandler.post {
            Log.d(TAG, "release() — recognizer=${speechRecognizer != null}")
            try {
                speechRecognizer?.setRecognitionListener(null)
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing recognizer: ${e.message}")
            }
            speechRecognizer = null
        }
    }

    fun getTranscriptSnapshot(): String {
        val current = _transcribedText.value.trim()
        if (current.isNotBlank()) return current
        val accumulated = synchronized(transcriptLock) { accumulatedText.toString().trim() }
        return accumulated
    }

    // -----------------------------
    // INTERNAL START
    // -----------------------------
    private fun createAndStart() {
        if (!isActive) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer NOT available on this device")
            return
        }

        // Tear down any previous instance before creating a new one.
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null

        if (sttIntent == null) {
            sttIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(listener)

        try {
            recognizer.startListening(sttIntent)
            Log.d(TAG, "createAndStart() — recognizer.startListening dispatched")
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed: ${e.message}")
        }
    }

    // -----------------------------
    // LISTENER
    // -----------------------------
    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            beganSpeech = true
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            Log.d(
                TAG,
                "STT error=${errorName(error)} active=$isActive " +
                        "partials=$partialCount results=$resultCount beganSpeech=$beganSpeech"
            )
            if (!isActive) return

            // Auto-restart on transient errors so the session keeps going.
            mainHandler.postDelayed({ createAndStart() }, 700)
        }

        override fun onResults(results: Bundle?) {
            val matches =
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            resultCount++

            if (!matches.isNullOrEmpty()) {
                val text = matches[0].trim()

                if (text.isNotEmpty()) {
                    synchronized(transcriptLock) {
                        if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                        accumulatedText.append(text)
                    }

                    _transcribedText.value = synchronized(transcriptLock) { accumulatedText.toString() }
                    Log.d("onresultstext", "onResults committed='${_transcribedText.value}'")
                } else {
                    Log.d(TAG, "onResults — empty match")
                }
            } else {
                Log.d(TAG, "onResults — no matches")
            }

            if (isActive) mainHandler.post { createAndStart() }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches =
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            if (!matches.isNullOrEmpty()) {
                val partial = matches[0]
                val accumulatedPrefix = synchronized(transcriptLock) { accumulatedText.toString() }
                val display =
                    if (accumulatedPrefix.isEmpty()) partial
                    else "$accumulatedPrefix $partial"

                _transcribedText.value = display
                partialCount++
                if (partialCount <= 3 || partialCount % 10 == 0) {
                    Log.d(TAG, "onPartialResults #$partialCount = '$partial'")
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
