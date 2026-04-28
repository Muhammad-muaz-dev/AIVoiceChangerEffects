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
        private const val MAX_RETRIES = 15
        private const val RETRY_DELAY_MS = 800L
        private const val RECREATE_DELAY_MS = 300L
        private const val SILENCE_THRESHOLD_DB = 2.0f
    }

    private var speechRecognizer: SpeechRecognizer? = null

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val _rms = MutableStateFlow(0f)
    val rms: StateFlow<Float> = _rms.asStateFlow()

    private var shouldContinueListening = false
    private var retryCount = 0
    private var currentLanguage: String? = null
    private var currentPartialText: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    fun startListening(language: String? = null, resetTranscript: Boolean = true) {

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available")
            return
        }

        forceDestroyRecognizer()

        if (resetTranscript) {
            _transcribedText.value = ""
        }
        _rms.value = 0f
        shouldContinueListening = true
        retryCount = 0
        currentLanguage = language
        if (resetTranscript) {
            currentPartialText = ""
        }

        mainHandler.postDelayed({
            createAndStartRecognizer()
        }, RECREATE_DELAY_MS)
    }

    fun stopListening(): String {

        shouldContinueListening = false
        mainHandler.removeCallbacksAndMessages(null)

        if (currentPartialText.isNotEmpty()) {
            val current = _transcribedText.value
            _transcribedText.value =
                if (current.isEmpty()) currentPartialText
                else "$current $currentPartialText"

            currentPartialText = ""
        }

        _rms.value = 0f
        forceDestroyRecognizer()

        return _transcribedText.value
    }

    private fun forceDestroyRecognizer() {
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    private fun createAndStartRecognizer() {

        if (!shouldContinueListening) return

        try {

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
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    _rms.value = if (rmsdB <= SILENCE_THRESHOLD_DB) 0f else rmsdB
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _rms.value = 0f
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "SpeechRecognizer error code=$error, retry=$retryCount/$MAX_RETRIES")
                    if (!shouldContinueListening) return

                    // Commit any partial results so they are not lost on error
                    if (currentPartialText.isNotEmpty()) {
                        val current = _transcribedText.value
                        _transcribedText.value =
                            if (current.isEmpty()) currentPartialText
                            else "$current $currentPartialText"
                        currentPartialText = ""
                    }

                    retryCount++
                    if (retryCount <= MAX_RETRIES) {
                        mainHandler.postDelayed({
                            createAndStartRecognizer()
                        }, RETRY_DELAY_MS)
                    } else {
                        Log.e(TAG, "Max retries reached — STT stopped")
                    }
                }

                override fun onResults(results: Bundle?) {

                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    if (!matches.isNullOrEmpty()) {
                        val newText = matches[0]
                        currentPartialText = ""

                        val current = _transcribedText.value
                        _transcribedText.value =
                            if (current.isEmpty()) newText
                            else "$current $newText"
                    }

                    if (shouldContinueListening) {
                        mainHandler.postDelayed({
                            createAndStartRecognizer()
                        }, RECREATE_DELAY_MS)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    if (!partial.isNullOrEmpty()) {
                        currentPartialText = partial[0]
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create/start recognizer: ${e.message}", e)
        }
    }
}