package com.example.aivoicechangersounds.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aivoicechangersounds.Viewmodels.VoiceAIViewModel
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.data.models.Voice
import com.example.aivoicechangersounds.ui.voiceai.LanguageAdapter
import com.example.aivoicechangersounds.ui.voiceai.VoiceGridAdapter
import com.example.aivoicechangersounds.utils.Resource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityVoiceAiactivityBinding
import com.voicechanger.app.databinding.BottomSheetLanguagesBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class VoiceAIActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceAiactivityBinding
    private val viewModel: VoiceAIViewModel by viewModels()

    private lateinit var voiceAdapter: VoiceGridAdapter

    private var languageList: List<Language> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVoiceAiactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVoiceGrid()
        setupTextWatcher()
        setupLanguageClick()
        setupGenerateButton()
        observeViewModel()

        binding.btnGenerateVoice.isEnabled = false
    }

    // ───────────────────────── Toolbar ─────────────────────────
    private fun setupToolbar() {
        binding.backarrow.setOnClickListener { finish() }
    }

    // ───────────────────────── Voice Grid ─────────────────────────
    private fun setupVoiceGrid() {
        voiceAdapter = VoiceGridAdapter { voice ->
            viewModel.selectVoice(voice)
        }

        binding.rvVoices.apply {
            layoutManager = GridLayoutManager(this@VoiceAIActivity, 3)
            adapter = voiceAdapter
        }
    }

    // ───────────────────────── Text watcher ─────────────────────────
    private fun setupTextWatcher() {
        binding.TtoChange.addTextChangedListener {
            val text = it.toString().trim()
            val isLoading = viewModel.generateResult.value is Resource.Loading

            binding.btnGenerateVoice.isEnabled = text.isNotEmpty() && !isLoading
        }
    }

    // ───────────────────────── Language click ─────────────────────────
    private fun setupLanguageClick() {
        binding.langselection.setOnClickListener {
            showLanguageBottomSheet()
        }
    }

    // ───────────────────────── Bottom sheet ─────────────────────────
    private fun showLanguageBottomSheet() {

        if (languageList.isEmpty()) {
            Toast.makeText(this, "Languages loading...", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetLanguagesBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val currentLang = viewModel.selectedLanguage.value

        val selectedIndex = languageList.indexOfFirst {
            it.code == currentLang?.code
        }.coerceAtLeast(0)

        val adapter = LanguageAdapter(languageList, selectedIndex) { selected ->

            binding.selectedlang.text = selected.displayName

            voiceAdapter.submitList(emptyList())

            viewModel.selectLanguage(selected)

            dialog.dismiss()
        }

        sheetBinding.rvLanguages.apply {
            layoutManager = LinearLayoutManager(this@VoiceAIActivity)
            this.adapter = adapter
        }

        sheetBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ───────────────────────── Generate ─────────────────────────
    private fun setupGenerateButton() {
        binding.btnGenerateVoice.setOnClickListener {

            val text = binding.TtoChange.text.toString().trim()

            if (text.isEmpty()) {
                binding.TtoChange.error = "Please enter text"
                return@setOnClickListener
            }

            viewModel.generateAudio(text)
        }
    }

    // ───────────────────────── Observers ─────────────────────────
    private fun observeViewModel() {

        // LANGUAGES
        viewModel.availableLanguages.observe(this) { resource ->
            when (resource) {

                is Resource.Success -> {
                    languageList = resource.data

                    if (languageList.isNotEmpty()) {
                        binding.selectedlang.text = languageList.first().displayName
                    }
                }

                is Resource.Error -> {
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }

                else -> Unit
            }
        }

        // VOICES
        viewModel.voices.observe(this) { resource ->
            when (resource) {

                is Resource.Loading -> {
                    binding.progressVoices.visibility = View.VISIBLE
                    binding.rvVoices.visibility = View.GONE
                }

                is Resource.Success -> {
                    binding.progressVoices.visibility = View.GONE
                    binding.rvVoices.visibility = View.VISIBLE

                    voiceAdapter.submitList(resource.data)

                    Log.d("voices list", "resource: ${resource.data}")

                    if (resource.data.isEmpty()) {
                        Toast.makeText(this, "No voices found", Toast.LENGTH_SHORT).show()
                    }
                }

                is Resource.Error -> {
                    binding.progressVoices.visibility = View.GONE
                    binding.rvVoices.visibility = View.GONE
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // SELECTED VOICE
        viewModel.selectedVoice.observe(this) { voice ->
            voiceAdapter.setSelectedVoice(voice)
        }

        // GENERATION RESULT
        viewModel.generateResult.observe(this) { resource ->

            when (resource) {

                is Resource.Loading -> {
                    binding.btnGenerateVoice.isEnabled = false
                    showGeneratingDialog()
                }

                is Resource.Success -> {

                    binding.btnGenerateVoice.isEnabled = true
                    hideGeneratingDialog()

                    val result = resource.data

                    navigateToPlayer(
                        result.audioUrl,
                        result.audioBase64,
                        result.filePath
                    )
                }

                is Resource.Error -> {
                    hideGeneratingDialog()

                    binding.btnGenerateVoice.isEnabled =
                        binding.TtoChange.text.toString().trim().isNotEmpty()

                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ───────────────────────── Navigation ─────────────────────────
    private fun navigateToPlayer(audioUrl: String?, audioBase64: String?, filePath: String?) {
        val intent = Intent(this, AudioPlayerActivity::class.java).apply {
            putExtra(AudioPlayerActivity.EXTRA_AUDIO_URL, audioUrl)
            putExtra(AudioPlayerActivity.EXTRA_AUDIO_BASE64, audioBase64)
            putExtra(AudioPlayerActivity.EXTRA_FILE_PATH, filePath)
            putExtra(AudioPlayerActivity.EXTRA_VOICE_NAME, viewModel.selectedVoice.value?.name ?: "")
            putExtra(AudioPlayerActivity.EXTRA_INPUT_TEXT, binding.TtoChange.text.toString())
        }
        startActivity(intent)
    }

    // ───────────────────────── Dialog ─────────────────────────
    private var loadingDialog: AlertDialog? = null

    private fun showGeneratingDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_generating_audio, null)

        loadingDialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.white)
        loadingDialog?.show()
    }

    private fun hideGeneratingDialog() {
        loadingDialog?.dismiss()
    }
}