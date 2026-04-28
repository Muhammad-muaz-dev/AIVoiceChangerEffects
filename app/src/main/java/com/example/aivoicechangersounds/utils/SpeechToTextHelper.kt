package com.example.aivoicechangersounds.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechToTextHelper @Inject constructor(
    @ApplicationContext private val context: Context
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText

    private val accumulatedText = StringBuilder()

    fun startListening(resetTranscript: Boolean = true) {
        if (resetTranscript) {
            accumulatedText.setLength(0)
            _transcribedText.value = ""
        }

        Handler(Looper.getMainLooper()).post {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@SpeechToTextHelper)
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    fun stopListening(): String {
        val result = accumulatedText.toString()
        Handler(Looper.getMainLooper()).post {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        return result
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) {
        // Automatically restart if not stopped by user
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
            accumulatedText.append(text)
            _transcribedText.value = accumulatedText.toString()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val partial = matches[0]
            val current = if (accumulatedText.isEmpty()) partial else "${accumulatedText} $partial"
            _transcribedText.value = current
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
