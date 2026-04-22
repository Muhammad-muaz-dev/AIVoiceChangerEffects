package com.example.aivoicechangersounds.ui.voiceai

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
import com.example.aivoicechangersounds.ui.audioplayer.AudioPlayerActivity
import com.example.aivoicechangersounds.utils.Resource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityVoiceAiactivityBinding
import com.voicechanger.app.databinding.BottomSheetLanguagesBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VoiceAIActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceAiactivityBinding
    private val viewModel: VoiceAIViewModel by viewModels()
    private lateinit var voiceAdapter: VoiceGridAdapter

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

        // NOTE: do NOT call viewModel.loadVoices() here.
        // The ViewModel's init block calls loadLanguagesAndVoices() which fetches
        // languages then voices in the correct order inside a single coroutine.
    }

    private fun setupToolbar() {
        binding.backarrow.setOnClickListener { finish() }
    }

    private fun setupVoiceGrid() {
        voiceAdapter = VoiceGridAdapter { voice ->
            viewModel.selectVoice(voice)
        }
        binding.rvVoices.apply {
            layoutManager = GridLayoutManager(this@VoiceAIActivity, 3)
            adapter = voiceAdapter
        }
    }

    private fun setupTextWatcher() {
        binding.TtoChange.addTextChangedListener {
            val text = it.toString().trim()
            val isLoading = viewModel.generateResult.value is Resource.Loading
            binding.btnGenerateVoice.isEnabled = text.isNotEmpty() && !isLoading
        }
    }

    private fun setupLanguageClick() {
        binding.langselection.setOnClickListener {
            showLanguageBottomSheet()
        }
    }

    private fun showLanguageBottomSheet() {
        val languagesResource = viewModel.availableLanguages.value
        val languages = if (languagesResource is Resource.Success) languagesResource.data else emptyList()

        if (languages.isEmpty()) {
            Toast.makeText(this, "Languages are still loading, please wait…", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetLanguagesBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        dialog.behavior.isDraggable = true

        val currentLang = viewModel.selectedLanguage.value
        val selectedIndex = languages.indexOfFirst { it.code == currentLang?.code }.coerceAtLeast(0)

        val adapter = LanguageAdapter(languages, selectedIndex) { selectedLanguage ->
            binding.selectedlang.text = selectedLanguage.displayName
            viewModel.selectLanguage(selectedLanguage)
            dialog.dismiss()
        }

        sheetBinding.rvLanguages.apply {
            layoutManager = LinearLayoutManager(this@VoiceAIActivity)
            this.adapter = adapter
        }
        sheetBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun setupGenerateButton() {
        binding.btnGenerateVoice.setOnClickListener {
            Log.d("VoiceAIActivity", "Generate button clicked")
            val text = binding.TtoChange.text.toString().trim()
            if (text.isEmpty()) {
                binding.TtoChange.error = "Please enter text"
                return@setOnClickListener
            }
            viewModel.generateAudio(text)
        }
    }

    private fun observeViewModel() {

        viewModel.voices.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> { /* optional: show spinner */ }
                is Resource.Success -> {
                    voiceAdapter.submitList(resource.data)
                    if (resource.data.isEmpty()) {
                        Toast.makeText(this, "No voices found for this language", Toast.LENGTH_SHORT).show()
                    }
                }
                is Resource.Error -> {
                    Toast.makeText(this, "Could not load voices: ${resource.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.selectedVoice.observe(this) { voice ->
            voiceAdapter.setSelectedVoice(voice)
        }

        viewModel.selectedLanguage.observe(this) { lang ->
            lang?.let { binding.selectedlang.text = it.displayName }
        }

        viewModel.availableLanguages.observe(this) { resource ->
            if (resource is Resource.Error) {
                Toast.makeText(this, "Could not load languages: ${resource.message}", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.generateResult.observe(this) { resource ->
            Log.d("VoiceAIActivity", "generateResult: ${resource.javaClass.simpleName}")
            when (resource) {
                is Resource.Loading -> {
                    binding.btnGenerateVoice.isEnabled = false
                    showGeneratingDialog()
                }
                is Resource.Success -> {
                    binding.btnGenerateVoice.isEnabled = true
                    hideGeneratingDialog()
                    navigateToPlayer(resource.data.audioUrl, resource.data.audioBase64)
                }
                is Resource.Error -> {
                    hideGeneratingDialog()
                    binding.btnGenerateVoice.isEnabled =
                        binding.TtoChange.text.toString().trim().isNotEmpty()
                    Toast.makeText(this, "Error: ${resource.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToPlayer(audioUrl: String?, audioBase64: String?) {
        val intent = Intent(this, AudioPlayerActivity::class.java).apply {
            putExtra("audioUrl", audioUrl)
            putExtra("audioBase64", audioBase64)
            putExtra("voiceName", viewModel.selectedVoice.value?.name ?: "")
            putExtra("inputText", binding.TtoChange.text.toString())
        }
        startActivity(intent)
    }

    private var loadingDialog: AlertDialog? = null

    private fun showGeneratingDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_generating_audio, null)
        loadingDialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
    }

    private fun hideGeneratingDialog() {
        loadingDialog?.dismiss()
    }
}