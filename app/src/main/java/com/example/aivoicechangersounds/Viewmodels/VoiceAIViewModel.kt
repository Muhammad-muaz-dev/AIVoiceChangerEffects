package com.example.aivoicechangersounds.Viewmodels

import androidx.lifecycle.*
import com.example.aivoicechangersounds.data.models.GenerateAudioResponse
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.data.models.Voice
import com.example.aivoicechangersounds.data.repository.VoiceRepository
import com.example.aivoicechangersounds.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceAIViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository
) : ViewModel() {

    // ───────────── Voices ─────────────
    private val _voices = MutableLiveData<Resource<List<Voice>>>()
    val voices: LiveData<Resource<List<Voice>>> = _voices

    // ───────────── Languages ─────────────
    private val _availableLanguages = MutableLiveData<Resource<List<Language>>>()
    val availableLanguages: LiveData<Resource<List<Language>>> = _availableLanguages

    // ───────────── Selected State ─────────────
    private val _selectedVoice = MutableLiveData<Voice?>()
    val selectedVoice: LiveData<Voice?> = _selectedVoice

    private val _selectedLanguage = MutableLiveData<Language?>()
    val selectedLanguage: LiveData<Language?> = _selectedLanguage

    // ───────────── Generate (FIXED) ─────────────
    private val _generateResult = MutableLiveData<Resource<GenerateAudioResponse>>()
    val generateResult: LiveData<Resource<GenerateAudioResponse>> = _generateResult

    init {
        loadInitialData()
    }

    // ───────────────────────── Load Data ─────────────────────────
    fun loadInitialData() {
        viewModelScope.launch {
            _availableLanguages.value = Resource.Loading
            _voices.value = Resource.Loading

            try {
                val languagesResult = voiceRepository.getLanguages()
                _availableLanguages.value = languagesResult

                if (languagesResult is Resource.Success) {
                    val languages = languagesResult.data
                    if (languages.isNotEmpty()) {
                        val defaultLang = languages.first()
                        _selectedLanguage.value = defaultLang
                        loadVoices(defaultLang.code)
                    } else {
                        loadVoices(null)
                    }
                }
            } catch (e: Exception) {
                _availableLanguages.value = Resource.Error(e.message ?: "Error")
                _voices.value = Resource.Error(e.message ?: "Error")
            }
        }
    }

    // ───────────────────────── Load Voices ─────────────────────────
    private fun loadVoices(languageCode: String?) {
        viewModelScope.launch {
            _voices.value = Resource.Loading
            val result = voiceRepository.getVoices(languageCode)
            _voices.value = result
            
            // Auto-select first voice if none selected
            if (result is Resource.Success && result.data.isNotEmpty()) {
                _selectedVoice.value = result.data.first()
            }
        }
    }

    // ───────────────────────── Language Change ─────────────────────────
    fun selectLanguage(language: Language) {
        _selectedLanguage.value = language
        loadVoices(language.code)
    }

    // ───────────────────────── Voice Select ─────────────────────────
    fun selectVoice(voice: Voice) {
        _selectedVoice.value = voice
    }

    // ───────────────────────── Generate Audio ─────────────────────────
    fun generateAudio(text: String) {

        val voice = _selectedVoice.value

        if (text.isBlank()) {
            _generateResult.value = Resource.Error("Please enter text")
            return
        }

        if (voice == null) {
            _generateResult.value = Resource.Error("Please select a voice")
            return
        }

        viewModelScope.launch {
            _generateResult.value = Resource.Loading

            val result = voiceRepository.generateAudio(
                text = text,
                model = voice.id
            )

            _generateResult.value = result
        }
    }
}