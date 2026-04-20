package com.example.aivoicechangersounds.ui.voiceai

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aivoicechangersounds.data.api.RetrofitClient
import com.example.aivoicechangersounds.data.repository.VoiceRepository
import com.example.aivoicechangersounds.ui.audioplayer.AudioPlayerActivity
import com.example.aivoicechangersounds.utils.Resource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityVoiceAiactivityBinding
import com.voicechanger.app.databinding.BottomSheetLanguagesBinding

class VoiceAIActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceAiactivityBinding
    private lateinit var viewModel: VoiceAIViewModel
    private lateinit var voiceAdapter: VoiceGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceAiactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViewModel()
        setupVoiceGrid()
        setupTextWatcher()
        setupLanguageClick()
        setupGenerateButton()
        observeViewModel()

        binding.btnGenerateVoice.isEnabled = false
        showGeneratingDialog()

        viewModel.loadVoices()

        viewModel.generateResult.observe(this) {
            hideGeneratingDialog()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarAIVoice)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Voice AI"
        binding.toolbarAIVoice.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupViewModel() {
        val repository = VoiceRepository(RetrofitClient.apiService)
        val factory = VoiceAIViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[VoiceAIViewModel::class.java]
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

    //  TEXT WATCHER (ENABLE BUTTON)
    private fun setupTextWatcher() {
        binding.TtoChange.addTextChangedListener {
            val text = it.toString().trim()
            binding.btnGenerateVoice.isEnabled = text.isNotEmpty()
        }
    }

    //  CLICK → OPEN BOTTOM SHEET
    private fun setupLanguageClick() {
        binding.langselection.setOnClickListener {
            showLanguageBottomSheet()
        }
    }

    //  BOTTOM SHEET
    private fun showLanguageBottomSheet() {

        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetLanguagesBinding.inflate(layoutInflater)

        dialog.setContentView(sheetBinding.root)

        val languages = viewModel.availableLanguages

        val adapter = LanguageAdapter(languages, 0) { selectedLanguage ->

            // Update UI
            binding.selectedlang.text = selectedLanguage.displayName

            // Update ViewModel
            viewModel.selectLanguage(selectedLanguage)

            dialog.dismiss()
        }

        sheetBinding.rvLanguages.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvLanguages.adapter = adapter

        sheetBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

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

    private fun observeViewModel() {

        viewModel.voices.observe(this) { resource ->
            when (resource) {
                is Resource.Success -> {
                    voiceAdapter.submitList(resource.data)
                }
                else -> {}
            }
        }

        viewModel.selectedVoice.observe(this) {
            voiceAdapter.setSelectedVoice(it)
        }

        viewModel.generateResult.observe(this) { resource ->
            when (resource) {

                is Resource.Loading -> {
                    binding.btnGenerateVoice.isEnabled = false
                }

                is Resource.Success -> {
                    binding.btnGenerateVoice.isEnabled = true

                    val audioData = resource.data
                    navigateToPlayer(audioData.audioUrl, audioData.audioBase64)
                }

                is Resource.Error -> {
                    binding.btnGenerateVoice.isEnabled = false
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
