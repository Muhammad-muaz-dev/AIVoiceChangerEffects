package com.example.aivoicechangersounds.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private var shouldContinueListening = false
    private var isCurrentlyListening = false

    /**
     * Starts speech recognition. Call from Main thread.
     * Resets any previously accumulated text.
     */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }

        _transcribedText.value = ""
        shouldContinueListening = true
        createAndStartRecognizer()
    }
    fun stopListening(): String {
        shouldContinueListening = false
        isCurrentlyListening = false

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer: ${e.message}")
        }
        speechRecognizer = null

        val result = _transcribedText.value
        Log.d(TAG, "Final transcribed text: $result")
        return result
    }

    private fun createAndStartRecognizer() {
        if (!shouldContinueListening) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
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
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // No-op: audio level changes
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // No-op
                }

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
                    Log.w(TAG, "Recognition error: $errorMsg")

                    // Auto-restart on recoverable errors (timeout, no match)
                    if (shouldContinueListening &&
                        (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    ) {
                        createAndStartRecognizer()
                    }
                }

                override fun onResults(results: Bundle?) {
                    isCurrentlyListening = false
                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    if (!matches.isNullOrEmpty()) {
                        val newText = matches[0]
                        val current = _transcribedText.value
                        _transcribedText.value = if (current.isEmpty()) {
                            newText
                        } else {
                            "$current $newText"
                        }
                        Log.d(TAG, "Recognized: $newText | Total: ${_transcribedText.value}")
                    }

                    // Restart to continue capturing while recording
                    if (shouldContinueListening) {
                        createAndStartRecognizer()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Partial results are available but we wait for final results
                    // to avoid duplicate text
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // No-op
                }
            })

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create/start recognizer: ${e.message}", e)
            isCurrentlyListening = false
        }
    }
}
