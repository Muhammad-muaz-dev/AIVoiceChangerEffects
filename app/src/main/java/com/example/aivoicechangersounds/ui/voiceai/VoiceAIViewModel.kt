package com.example.aivoicechangersounds.ui.voiceai


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aivoicechangersounds.data.models.GenerateAudioResponse
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.data.models.Voice
import com.example.aivoicechangersounds.data.repository.VoiceRepository
import com.example.aivoicechangersounds.utils.Resource

import kotlinx.coroutines.launch

class VoiceAIViewModel(private val repository: VoiceRepository) : ViewModel() {

    private val _voices = MutableLiveData<Resource<List<Voice>>>()
    val voices: LiveData<Resource<List<Voice>>> = _voices

    private val _generateResult = MutableLiveData<Resource<GenerateAudioResponse>>()
    val generateResult: LiveData<Resource<GenerateAudioResponse>> = _generateResult

    private val _selectedVoice = MutableLiveData<Voice?>()
    val selectedVoice: LiveData<Voice?> = _selectedVoice

    private val _selectedLanguage = MutableLiveData<Language?>()
    val selectedLanguage: LiveData<Language?> = _selectedLanguage

    val availableLanguages: List<Language> = listOf(
        Language("en", "English"),
        Language("es", "Spanish"),
        Language("fr", "French"),
        Language("de", "German"),
        Language("it", "Italian"),
        Language("pt", "Portuguese"),
        Language("ar", "Arabic"),
        Language("hi", "Hindi"),
        Language("zh", "Chinese"),
        Language("ja", "Japanese"),
        Language("ko", "Korean"),
        Language("ru", "Russian")
    )
    fun loadVoices(language: String? = null) {
        viewModelScope.launch {
            _voices.value = Resource.Loading
            _voices.value = repository.getVoices(language)
        }
    }

    fun selectVoice(voice: Voice) {
        _selectedVoice.value = voice
    }

    fun selectLanguage(language: Language) {
        _selectedLanguage.value = language
        loadVoices(language.code)
    }

    fun generateAudio(text: String) {
        val voice = _selectedVoice.value
        val language = _selectedLanguage.value

        if (voice == null) {
            _generateResult.value = Resource.Error("Please select a voice")
            return
        }
        if (language == null) {
            _generateResult.value = Resource.Error("Please select a language")
            return
        }
        if (text.isBlank()) {
            _generateResult.value = Resource.Error("Please enter some text")
            return
        }

        viewModelScope.launch {
            _generateResult.value = Resource.Loading
            _generateResult.value = repository.generateAudio(text, voice.id, language.code)
        }
    }
}
