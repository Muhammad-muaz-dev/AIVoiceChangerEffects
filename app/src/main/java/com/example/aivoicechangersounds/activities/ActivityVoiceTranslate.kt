package com.example.aivoicechangersounds.activities
import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aivoicechangersounds.Viewmodels.VoiceTranslateViewModel
import com.example.aivoicechangersounds.adapters.TranslationHistoryAdapter
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.data.models.TranslationHistoryItem
import com.example.aivoicechangersounds.ui.voiceai.LanguageAdapter
import com.example.aivoicechangersounds.utils.Resource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityVoiceTranslateBinding
import com.voicechanger.app.databinding.BottomSheetLanguagesBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ActivityVoiceTranslate : AppCompatActivity() {

    companion object {
        private const val TAG = "VoiceTranslate"
    }

    private lateinit var binding: ActivityVoiceTranslateBinding
    private val viewModel: VoiceTranslateViewModel by viewModels()

    private var languageList: List<Language> = emptyList()
    private var selectedSourceIndex: Int = 0
    private var selectedTargetIndex: Int = 0

    private lateinit var historyAdapter: TranslationHistoryAdapter
    private val historyItems: MutableList<TranslationHistoryItem> = mutableListOf()

    private var speechRecognizer: SpeechRecognizer? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showSpeakingDialog()
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVoiceTranslateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }
    private fun setUpToolbar(){
        binding.btnback.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = TranslationHistoryAdapter(historyItems) { position ->
            historyAdapter.removeItem(position)
            updateEmptyState()
        }

        binding.recyclehistory.apply {
            layoutManager = LinearLayoutManager(this@ActivityVoiceTranslate)
            adapter = historyAdapter
        }
    }

    private fun setupClickListeners() {
        binding.langofspeak.setOnClickListener {
            if (languageList.isNotEmpty()) {
                showLanguageBottomSheet(isSource = true)
            }
        }

        binding.langoftranslate.setOnClickListener {
            if (languageList.isNotEmpty()) {
                showLanguageBottomSheet(isSource = false)
            }
        }

        binding.langofspeakuing.setOnClickListener {
            checkPermissionAndSpeak()
        }

        binding.langoftranslation.setOnClickListener {
            viewModel.translate()
        }
    }

    private fun observeViewModel() {
        viewModel.languages.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    languageList = result.data
                    if (languageList.isNotEmpty()) {
                        binding.langofspeak.text = languageList.first().displayName
                        binding.langoftranslate.text = languageList.first().displayName
                    }
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                is Resource.Loading -> { }
            }
        }

        viewModel.sourceLanguage.observe(this) { lang ->
            lang?.let { binding.langofspeak.text = it.displayName }
        }

        viewModel.targetLanguage.observe(this) { lang ->
            lang?.let { binding.langoftranslate.text = it.displayName }
        }

        viewModel.translateResult.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    val sourceText = viewModel.capturedText.value ?: ""
                    val sourceLang = viewModel.sourceLanguage.value?.displayName ?: ""
                    val targetLang = viewModel.targetLanguage.value?.displayName ?: ""

                    val item = TranslationHistoryItem(
                        sourceLanguage = sourceLang,
                        sourceText = sourceText,
                        targetLanguage = targetLang,
                        translatedText = result.data
                    )
                    historyAdapter.addItem(item)
                    binding.recyclehistory.scrollToPosition(0)
                    updateEmptyState()
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
                is Resource.Loading -> {
                    Toast.makeText(this, "Translating...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateEmptyState() {
        if (historyAdapter.itemCount > 0) {
            binding.imgFrame.visibility = View.GONE
            binding.recyclehistory.visibility = View.VISIBLE
        } else {
            binding.imgFrame.visibility = View.VISIBLE
            binding.recyclehistory.visibility = View.GONE
        }
    }

    // ───────────── Language Bottom Sheet ─────────────

    private fun showLanguageBottomSheet(isSource: Boolean) {
        val bottomSheet = BottomSheetDialog(this)
        val sheetBinding = BottomSheetLanguagesBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        val currentIndex = if (isSource) selectedSourceIndex else selectedTargetIndex

        val adapter = LanguageAdapter(languageList, currentIndex) { language ->
            if (isSource) {
                viewModel.selectSourceLanguage(language)
                selectedSourceIndex = languageList.indexOf(language)
            } else {
                viewModel.selectTargetLanguage(language)
                selectedTargetIndex = languageList.indexOf(language)
            }
            bottomSheet.dismiss()
        }

        sheetBinding.rvLanguages.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvLanguages.adapter = adapter

        sheetBinding.btnClose.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    // ───────────── Speaking Dialog with STT ─────────────

    private fun checkPermissionAndSpeak() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            showSpeakingDialog()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun showSpeakingDialog() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialogue_speaking_sheet)
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val speakingText = dialog.findViewById<TextView>(R.id.speakingtext)
        val languageLabel = dialog.findViewById<TextView>(R.id.languageLabel)

        val sourceLang = viewModel.sourceLanguage.value
        languageLabel.text = sourceLang?.displayName ?: "English"
        speakingText.text = ""

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            sourceLang?.code?.let { code ->
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, code)
            }
        }

        val accumulatedText = StringBuilder()

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended")
            }

            override fun onError(error: Int) {
                Log.w(TAG, "Speech error: $error")
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    if (dialog.isShowing) {
                        recognizer.startListening(recognizerIntent)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                if (!matches.isNullOrEmpty()) {
                    val newText = matches[0]
                    if (accumulatedText.isNotEmpty()) {
                        accumulatedText.append(" ")
                    }
                    accumulatedText.append(newText)
                    speakingText.text = accumulatedText.toString()
                    Log.d(TAG, "Recognized: $newText | Total: $accumulatedText")
                }

                val finalText = accumulatedText.toString()
                if (finalText.isNotBlank()) {
                    viewModel.setCapturedText(finalText)
                }

                if (dialog.isShowing) {
                    recognizer.startListening(recognizerIntent)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                if (!partial.isNullOrEmpty()) {
                    val currentDisplay = if (accumulatedText.isEmpty()) {
                        partial[0]
                    } else {
                        "${accumulatedText} ${partial[0]}"
                    }
                    speakingText.text = currentDisplay
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        dialog.setOnDismissListener {
            val finalText = accumulatedText.toString()
            if (finalText.isNotBlank()) {
                viewModel.setCapturedText(finalText)
            }
            try {
                recognizer.stopListening()
                recognizer.cancel()
                recognizer.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up recognizer: ${e.message}")
            }
            speechRecognizer = null
        }

        dialog.show()
        recognizer.startListening(recognizerIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying recognizer: ${e.message}")
        }
        speechRecognizer = null
        historyAdapter.shutdown()
    }
}
