package com.example.aivoicechangersounds.ui.voiceai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aivoicechangersounds.ui.audioplayer.AudioPlayerActivity
import com.example.aivoicechangersounds.utils.Resource
import com.google.android.material.bottomsheet.BottomSheetBehavior
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

        viewModel.loadVoices()

        viewModel.generateResult.observe(this) {
            hideGeneratingDialog()
        }
    }

    private fun setupToolbar() {
    binding.backarrow.setOnClickListener {
        finish()
    }
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
            // Only disable button if not loading, to avoid conflicts with API state
            val isLoading = viewModel.generateResult.value is Resource.Loading
            binding.btnGenerateVoice.isEnabled = text.isNotEmpty() && !isLoading
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

        // ==================== IMPORTANT FIXES START HERE ====================

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        }

        // Optional: Make it expanded by default and prevent dragging down from top
        dialog.behavior.isDraggable = true

        val languagesResource = viewModel.availableLanguages.value
        val languages = if (languagesResource is Resource.Success) languagesResource.data else emptyList()

        val adapter = LanguageAdapter(languages, 0) { selectedLanguage ->
            binding.selectedlang.text = selectedLanguage.displayName
            viewModel.selectLanguage(selectedLanguage)
            dialog.dismiss()
        }

        sheetBinding.rvLanguages.apply {
            layoutManager = LinearLayoutManager(this@VoiceAIActivity)  // Use correct context
            this.adapter = adapter
        }

        sheetBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

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

            Log.d("VoiceAIActivity", "Calling generateAudio with text: $text")
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

        viewModel.selectedVoice.observe(this) { selectedVoice ->
            voiceAdapter.setSelectedVoice(selectedVoice)
        }
        
        // Also observe selected language to update layout
        viewModel.selectedLanguage.observe(this) { selectedLanguage ->
            selectedLanguage?.let { lang ->
                binding.selectedlang.text = lang.displayName
            }
        }

        viewModel.generateResult.observe(this) { resource ->
            Log.d("VoiceAIActivity", "generateResult state changed: ${resource.javaClass.simpleName}")
            when (resource) {

                is Resource.Loading -> {
                    Log.d("VoiceAIActivity", "Loading state: showing dialog, disabling button")
                    binding.btnGenerateVoice.isEnabled = false
                    showGeneratingDialog()
                }

                is Resource.Success -> {
                    Log.d("VoiceAIActivity", "Success state: hiding dialog, enabling button, navigating to player")
                    binding.btnGenerateVoice.isEnabled = true
                    hideGeneratingDialog() // Hide loading dialog

                    val audioData = resource.data
                    navigateToPlayer(audioData.audioUrl, audioData.audioBase64)
                }

                is Resource.Error -> {
                    Log.d("VoiceAIActivity", "Error state: hiding dialog, checking text for button enable")
                    hideGeneratingDialog() // Hide loading dialog
                    // Re-enable button if there's text, allowing user to try again
                    val currentText = binding.TtoChange.text.toString().trim()
                    binding.btnGenerateVoice.isEnabled = currentText.isNotEmpty()
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
