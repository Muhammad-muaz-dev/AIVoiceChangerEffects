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
        loadLanguagesAndVoices()
    }

    /**
     * Fetches languages then voices in a single coroutine — no nested launches,
     * no race conditions. The voices call waits for the language result before running.
     */
    fun loadLanguagesAndVoices() {
        viewModelScope.launch {
            // Step 1: load languages
            _availableLanguages.value = Resource.Loading
            val langResult = voiceRepository.getLanguages()
            _availableLanguages.value = langResult

            // Step 2: determine which language code to filter voices by
            val languageCode: String? = if (langResult is Resource.Success && langResult.data.isNotEmpty()) {
                // Only set default if user has not already picked one
                if (_selectedLanguage.value == null) {
                    _selectedLanguage.value = langResult.data.first()
                }
                _selectedLanguage.value?.code
            } else {
                if (langResult is Resource.Error) {
                    Log.e("VoiceAIViewModel", "Languages failed: ${langResult.message}")
                }
                null  // fetch all voices as fallback
            }

            // Step 3: load voices (waits here because it is in the same coroutine)
            _voices.value = Resource.Loading
            val voicesResult = voiceRepository.getVoices(languageCode)
            _voices.value = voicesResult

            if (voicesResult is Resource.Error) {
                Log.e("VoiceAIViewModel", "Voices failed: ${voicesResult.message}")
            }
        }
    }

    /**
     * Called when the user picks a language from the bottom sheet.
     */
    fun selectLanguage(language: Language) {
        _selectedLanguage.value = language
        viewModelScope.launch {
            _voices.value = Resource.Loading
            val result = voiceRepository.getVoices(language.code)
            _voices.value = result
            if (result is Resource.Error) {
                Log.e("VoiceAIViewModel", "Voices failed after lang change: ${result.message}")
            }
        }
    }

    fun selectVoice(voice: Voice) {
        _selectedVoice.value = voice
    }

    fun generateAudio(text: String) {
        val voice = _selectedVoice.value
        val language = _selectedLanguage.value

        Log.d("VoiceAIViewModel", "generateAudio: text='$text' voice=${voice?.name} lang=${language?.displayName}")

        if (text.isBlank()) {
            _generateResult.value = Resource.Error("Please enter some text")
            return
        }

        viewModelScope.launch {
            _generateResult.value = Resource.Loading
            val voiceId = voice?.id ?: "1"
            val languageCode = language?.code ?: "en"
            _generateResult.value = voiceRepository.generateAudio(text, voiceId, languageCode)
        }
    }
}