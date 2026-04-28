package com.example.aivoicechangersounds.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.aivoicechangersounds.Viewmodels.RecordingViewModel
import com.example.aivoicechangersounds.data.models.RecordingState
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityRecordingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private val viewModel: RecordingViewModel by viewModels()

    // ───────────── Speech-to-Text (Direct Flow like ActivityVoiceTranslate) ─────────────
    private var speechRecognizer: SpeechRecognizer? = null
    private var sttIntent: Intent? = null
    private val accumulatedText = StringBuilder()
    private var isSTTActive = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                viewModel.onAudioButtonClicked()
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpToolbar()
        setupClickListeners()
        observeViewModel()
    }

    private fun setUpToolbar() {
        binding.btnback.setOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        binding.btnaudio.setOnClickListener {
            if (hasRecordPermission()) {
                viewModel.onAudioButtonClicked()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        binding.btncancel.setOnClickListener {
            viewModel.onCancelClicked()
        }

        binding.btndone.setOnClickListener {
            viewModel.onDoneClicked()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recordingState.collect { state ->
                        updateUI(state)
                    }
                }
                launch {
                    viewModel.formattedTime.collect { time ->
                        binding.tvTimer.text = time
                    }
                }
                launch {
                    viewModel.liveAmplitude.collect { amplitude ->
                        val isRecording = viewModel.recordingState.value is RecordingState.Recording
                        val scaledAmplitude = if (!isRecording || (amplitude <= 0)) 0 else amplitude.coerceIn(0, 32767)
                        binding.waveformView.addAmplitude(scaledAmplitude)
                    }
                }
                launch {
                    viewModel.liveTranscribedText.collect { text ->
                        binding.tvTranscribed.text = text
                    }
                }
            }
        }
    }

    private fun updateUI(state: RecordingState) {
        when (state) {
            is RecordingState.Idle -> {
                binding.btnaudio.setImageResource(R.drawable.ic_audio)
                binding.btncancel.visibility = View.GONE
                binding.btndone.visibility = View.GONE
                binding.waveformView.reset()
                stopSTT()
            }

            is RecordingState.Recording -> {
                binding.btnaudio.setImageResource(R.drawable.ic_pause)
                binding.btncancel.visibility = View.VISIBLE
                binding.btndone.visibility = View.VISIBLE
                startSTT()
            }

            is RecordingState.Paused -> {
                binding.btnaudio.setImageResource(R.drawable.ic_play)
                binding.btncancel.visibility = View.VISIBLE
                binding.btndone.visibility = View.VISIBLE
                pauseSTT()
            }

            is RecordingState.Done -> {
                stopSTT()
                navigateToNextScreen(state.filePath, state.transcribedText)
            }

            is RecordingState.Cancelled -> {
                binding.btnaudio.setImageResource(R.drawable.ic_audio)
                binding.btncancel.visibility = View.GONE
                binding.btndone.visibility = View.GONE
                binding.waveformView.reset()
                stopSTT()
            }

            is RecordingState.Error -> {
                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                binding.btnaudio.setImageResource(R.drawable.ic_audio)
                binding.btncancel.visibility = View.GONE
                binding.btndone.visibility = View.GONE
                binding.waveformView.reset()
                stopSTT()
            }
        }
    }

    // ───────────── Speech-to-Text Logic (Exact same flow as ActivityVoiceTranslate) ─────────────

    private fun startSTT() {
        if (isSTTActive) return
        isSTTActive = true

        // Exactly like ActivityVoiceTranslate: Create a new instance every time we start/resume
        createAndStartRecognizer()
    }

    private fun createAndStartRecognizer() {
        if (!isSTTActive) return

        try {
            // Cleanup old instance if any
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()

            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.w("RecordingActivity", "Speech recognition not available")
                return
            }

            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = recognizer

            if (sttIntent == null) {
                sttIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("RecordingActivity", "onReadyForSpeech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d("RecordingActivity", "onBeginningOfSpeech")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.d("RecordingActivity", "STT Error: $error")
                    // If match not found or timeout, restart like in ActivityVoiceTranslate
                    if (isSTTActive && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                        recognizer.startListening(sttIntent)
                    } else if (isSTTActive && error == SpeechRecognizer.ERROR_AUDIO) {
                        // Mic conflict - wait and retry once
                        binding.root.postDelayed({
                            if (isSTTActive) recognizer.startListening(sttIntent)
                        }, 500)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                        accumulatedText.append(text)

                        viewModel.setTranscribedText(accumulatedText.toString())
                        Log.d("RecordingActivity", "Result: $text | Total: $accumulatedText")
                    }

                    if (isSTTActive) {
                        recognizer.startListening(sttIntent)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!partial.isNullOrEmpty()) {
                        val currentDisplay = if (accumulatedText.isEmpty()) {
                            partial[0]
                        } else {
                            "$accumulatedText ${partial[0]}"
                        }
                        viewModel.setTranscribedText(currentDisplay)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(sttIntent)

        } catch (e: Exception) {
            Log.e("RecordingActivity", "Failed to start recognizer: ${e.message}")
        }
    }

    private fun pauseSTT() {
        isSTTActive = false
        speechRecognizer?.stopListening()
    }

    private fun stopSTT() {
        isSTTActive = false
        accumulatedText.setLength(0)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun navigateToNextScreen(filePath: String, transcribedText: String) {
        val intent = Intent(this, VoiceEffectActivity::class.java).apply {
            putExtra(EXTRA_AUDIO_FILE_PATH, filePath)
            putExtra(EXTRA_TRANSCRIBED_TEXT, transcribedText)
        }
        startActivity(intent)
        finish()
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSTT()
    }

    companion object {
        const val EXTRA_AUDIO_FILE_PATH = "extra_audio_file_path"
        const val EXTRA_TRANSCRIBED_TEXT = "extra_transcribed_text"
    }
}
