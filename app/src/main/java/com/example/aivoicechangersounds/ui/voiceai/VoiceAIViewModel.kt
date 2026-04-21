package com.example.aivoicechangersounds.ui.voiceai


import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _voices = MutableLiveData<Resource<List<Voice>>>()
    val voices: LiveData<Resource<List<Voice>>> = _voices

    private val _generateResult = MutableLiveData<Resource<GenerateAudioResponse>>()
    val generateResult: LiveData<Resource<GenerateAudioResponse>> = _generateResult

    private val _selectedVoice = MutableLiveData<Voice?>()
    val selectedVoice: LiveData<Voice?> = _selectedVoice

    private val _selectedLanguage = MutableLiveData<Language?>()
    val selectedLanguage: LiveData<Language?> = _selectedLanguage

    private val _availableLanguages = MutableLiveData<Resource<List<Language>>>()
    val availableLanguages: LiveData<Resource<List<Language>>> = _availableLanguages

    init {
        loadLanguages()
    }

    fun loadLanguages() {
        viewModelScope.launch {
            _availableLanguages.value = Resource.Loading
            _availableLanguages.value = voiceRepository.getLanguages()
            
            // Set default language if available
            val resource = _availableLanguages.value
            if (resource is Resource.Success && resource.data.isNotEmpty() && _selectedLanguage.value == null) {
                selectLanguage(resource.data.first())
            }
        }
    }

    fun loadVoices(language: String? = null) {
        viewModelScope.launch {
            _voices.value = Resource.Loading
            _voices.value = voiceRepository.getVoices(language)
        }
    }

    fun selectVoice(voice: Voice) {
        viewModelScope.launch {
            _selectedVoice.value = voice
        }
    }

    fun selectLanguage(language: Language) {
        viewModelScope.launch {
            _selectedLanguage.value = language
            loadVoices(language.code)
        }
    }

    fun generateAudio(text: String) {
        val voice = _selectedVoice.value
        val language = _selectedLanguage.value

        Log.d("VoiceAIViewModel", "generateAudio called with text: '$text', voice: ${voice?.name}, language: ${language?.displayName}")

        if (text.isBlank()) {
            _generateResult.value = Resource.Error("Please enter some text")
            return
        }

        viewModelScope.launch {
            _generateResult.value = Resource.Loading
            Log.d("VoiceAIViewModel", "Set loading state")
            
            val voiceId = voice?.id ?: "1"
            val languageCode = language?.code ?: "en"
            
            Log.d("VoiceAIViewModel", "Calling VoiceRepository generateAudio with voiceId: $voiceId, languageCode: $languageCode")
            
            _generateResult.value = voiceRepository.generateAudio(text, voiceId, languageCode)
        }
    }
}
