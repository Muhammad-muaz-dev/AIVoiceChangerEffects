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
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechToTextHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SpeechToTextHelper"
        private const val MAX_RETRIES = 8
        private const val RETRY_DELAY_MS = 1000L
        private const val RECREATE_DELAY_MS = 500L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var shouldContinueListening = false
    private var isCurrentlyListening = false
    private var retryCount = 0
    private var currentLanguage: String? = null

    // Tracks the latest partial result for the current utterance.
    // Partial results replace each other (not additive). When onResults fires,
    // it gives the final text for that utterance and we clear this.
    // When stopListening() is called before onResults fires, we use this
    // as fallback so the user's speech is never lost.
    private var currentPartialText: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    fun startListening(language: String? = null) {
        Log.d(TAG, "startListening() called, language=$language")

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }

        // Full cleanup of any previous session
        forceDestroyRecognizer()

        _transcribedText.value = ""
        _isReady.value = false
        shouldContinueListening = true
        isCurrentlyListening = false
        retryCount = 0
        currentLanguage = language
        currentPartialText = ""

        // Small delay before creating a fresh recognizer to avoid device-level conflicts
        mainHandler.postDelayed({
            createAndStartRecognizer()
        }, RECREATE_DELAY_MS)
    }

    fun stopListening(): String {
        Log.d(TAG, "stopListening() called")
        shouldContinueListening = false
        isCurrentlyListening = false
        _isReady.value = false
        mainHandler.removeCallbacksAndMessages(null)

        // Flush any unsaved partial result BEFORE destroying the recognizer.
        // If the user presses Done while still speaking, onResults hasn't fired
        // yet — only onPartialResults has. This ensures we capture that text.
        if (currentPartialText.isNotEmpty()) {
            val current = _transcribedText.value
            _transcribedText.value = if (current.isEmpty()) {
                currentPartialText
            } else {
                "$current $currentPartialText"
            }
            Log.d(TAG, "Flushed partial text: '$currentPartialText' → total: '${_transcribedText.value}'")
            currentPartialText = ""
        }

        forceDestroyRecognizer()

        val result = _transcribedText.value
        Log.d(TAG, "Final transcribed text: '$result'")
        return result
    }

    private fun forceDestroyRecognizer() {
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying recognizer: ${e.message}")
        }
        speechRecognizer = null
    }

    private fun createAndStartRecognizer() {
        if (!shouldContinueListening) {
            Log.d(TAG, "createAndStartRecognizer: shouldContinueListening=false, skipping")
            return
        }

        try {
            // Destroy old instance cleanly before creating new one
            forceDestroyRecognizer()

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                if (!currentLanguage.isNullOrBlank()) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
                }
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    15000L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    5000L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    5000L
                )
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isCurrentlyListening = true
                    _isReady.value = true
                    retryCount = 0
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech ended")
                    isCurrentlyListening = false
                }

                override fun onError(error: Int) {
                    isCurrentlyListening = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        else -> "Unknown error ($error)"
                    }
                    Log.w(TAG, "Recognition error: $errorMsg (code=$error), " +
                            "accumulated='${_transcribedText.value}', partial='$currentPartialText'")

                    if (!shouldContinueListening) return

                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        Log.e(TAG, "Insufficient permissions — cannot retry")
                        return
                    }

                    // For silence/no-match, restart immediately (no retry count)
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        scheduleRestart(RECREATE_DELAY_MS)
                        return
                    }

                    // For all other errors, retry with delay up to MAX_RETRIES
                    retryCount++
                    if (retryCount <= MAX_RETRIES) {
                        Log.d(TAG, "Retrying after $errorMsg (attempt $retryCount/$MAX_RETRIES)")
                        scheduleRestart(RETRY_DELAY_MS)
                    } else {
                        Log.e(TAG, "Max retries ($MAX_RETRIES) reached for $errorMsg. Giving up.")
                    }
                }

                override fun onResults(results: Bundle?) {
                    isCurrentlyListening = false
                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    if (!matches.isNullOrEmpty()) {
                        val newText = matches[0]
                        // onResults gives final text for this utterance — clear partial
                        currentPartialText = ""
                        val current = _transcribedText.value
                        _transcribedText.value = if (current.isEmpty()) {
                            newText
                        } else {
                            "$current $newText"
                        }
                        Log.d(TAG, "FINAL result: '$newText' | Total: '${_transcribedText.value}'")
                    }

                    if (shouldContinueListening) {
                        scheduleRestart(RECREATE_DELAY_MS)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    if (!partial.isNullOrEmpty() && partial[0].isNotEmpty()) {
                        currentPartialText = partial[0]
                        Log.d(TAG, "Partial: '$currentPartialText' | Accumulated: '${_transcribedText.value}'")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
            Log.d(TAG, "startListening() called on recognizer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create/start recognizer: ${e.message}", e)
            isCurrentlyListening = false

            if (shouldContinueListening) {
                retryCount++
                if (retryCount <= MAX_RETRIES) {
                    Log.d(TAG, "Retrying creation (attempt $retryCount/$MAX_RETRIES)")
                    mainHandler.postDelayed({
                        createAndStartRecognizer()
                    }, RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!shouldContinueListening) return
        mainHandler.postDelayed({
            createAndStartRecognizer()
        }, delayMs)
    }
}
