package com.example.aivoicechangersounds.ui.voiceai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.example.aivoicechangersounds.data.api.RetrofitClient
import com.example.aivoicechangersounds.data.repository.VoiceRepository
import com.example.aivoicechangersounds.ui.audioplayer.AudioPlayerActivity
import com.example.aivoicechangersounds.utils.Resource
import com.voicechanger.app.databinding.ActivityVoiceAiactivityBinding

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
        setupLanguageSpinner()
        setupGenerateButton()
        observeViewModel()

        viewModel.loadVoices()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Voice AI"
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
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
        binding.recyclerViewVoices.apply {
            layoutManager = GridLayoutManager(this@VoiceAIActivity, 3)
            adapter = voiceAdapter
        }
    }

    private fun setupLanguageSpinner() {
        val languages = viewModel.availableLanguages
        val displayNames = languages.map { it.displayName }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            displayNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerLanguage.adapter = adapter
        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.selectLanguage(languages[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Select first language by default
        if (languages.isNotEmpty()) {
            viewModel.selectLanguage(languages[0])
        }
    }

    private fun setupGenerateButton() {
        binding.buttonGenerate.setOnClickListener {
            val text = binding.editTextInput.text.toString().trim()
            if (text.isEmpty()) {
                binding.editTextInput.error = "Please enter text"
                return@setOnClickListener
            }
            viewModel.generateAudio(text)
        }
    }

    private fun observeViewModel() {
        viewModel.voices.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarVoices.visibility = View.VISIBLE
                    binding.recyclerViewVoices.visibility = View.GONE
                    binding.textViewVoicesError.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBarVoices.visibility = View.GONE
                    binding.recyclerViewVoices.visibility = View.VISIBLE
                    binding.textViewVoicesError.visibility = View.GONE
                    voiceAdapter.submitList(resource.data)
                }
                is Resource.Error -> {
                    binding.progressBarVoices.visibility = View.GONE
                    binding.recyclerViewVoices.visibility = View.GONE
                    binding.textViewVoicesError.visibility = View.VISIBLE
                    binding.textViewVoicesError.text = resource.message
                }
            }
        }

        viewModel.selectedVoice.observe(this) { voice ->
            voiceAdapter.setSelectedVoice(voice)
        }

        viewModel.generateResult.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.buttonGenerate.isEnabled = false
                    binding.progressBarGenerate.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.buttonGenerate.isEnabled = true
                    binding.progressBarGenerate.visibility = View.GONE
                    val audioData = resource.data
                    navigateToPlayer(audioData.audioUrl, audioData.audioBase64)
                }
                is Resource.Error -> {
                    binding.buttonGenerate.isEnabled = true
                    binding.progressBarGenerate.visibility = View.GONE
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToPlayer(audioUrl: String?, audioBase64: String?) {
        if (audioUrl == null && audioBase64 == null) {
            Toast.makeText(this, "No audio data received", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedVoice = viewModel.selectedVoice.value
        val intent = Intent(this, AudioPlayerActivity::class.java).apply {
            putExtra(AudioPlayerActivity.EXTRA_AUDIO_URL, audioUrl)
            putExtra(AudioPlayerActivity.EXTRA_AUDIO_BASE64, audioBase64)
            putExtra(AudioPlayerActivity.EXTRA_VOICE_NAME, selectedVoice?.name ?: "")
            putExtra(AudioPlayerActivity.EXTRA_INPUT_TEXT, binding.editTextInput.text.toString())
        }
        startActivity(intent)
    }
}
