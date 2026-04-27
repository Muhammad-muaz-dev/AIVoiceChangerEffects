package com.example.aivoicechangersounds.Viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.data.repository.TranslateRepository
import com.example.aivoicechangersounds.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceTranslateViewModel @Inject constructor(
    private val translateRepository: TranslateRepository
) : ViewModel() {

    // ───────────── Languages ─────────────
    private val _languages = MutableLiveData<Resource<List<Language>>>()
    val languages: LiveData<Resource<List<Language>>> = _languages

    // ───────────── Selected Languages ─────────────
    private val _sourceLanguage = MutableLiveData<Language?>()
    val sourceLanguage: LiveData<Language?> = _sourceLanguage

    private val _targetLanguage = MutableLiveData<Language?>()
    val targetLanguage: LiveData<Language?> = _targetLanguage

    // ───────────── Captured Text (from STT) ─────────────
    private val _capturedText = MutableLiveData<String>()
    val capturedText: LiveData<String> = _capturedText

    // ───────────── Translation Result ─────────────
    private val _translateResult = MutableLiveData<Resource<String>>()
    val translateResult: LiveData<Resource<String>> = _translateResult

    init {
        fetchLanguages()
    }

    fun fetchLanguages() {
        viewModelScope.launch {
            _languages.value = Resource.Loading
            val result = translateRepository.getLanguages()
            _languages.value = result

            if (result is Resource.Success && result.data.isNotEmpty()) {
                if (_sourceLanguage.value == null) {
                    _sourceLanguage.value = result.data.first()
                }
                if (_targetLanguage.value == null) {
                    _targetLanguage.value = result.data.first()
                }
            }
        }
    }

    fun selectSourceLanguage(language: Language) {
        _sourceLanguage.value = language
    }

    fun selectTargetLanguage(language: Language) {
        _targetLanguage.value = language
    }

    fun setCapturedText(text: String) {
        _capturedText.value = text
    }

    fun translate() {
        val text = _capturedText.value
        val targetLang = _targetLanguage.value

        if (text.isNullOrBlank()) {
            _translateResult.value = Resource.Error("No text captured. Please speak first.")
            return
        }

        if (targetLang == null) {
            _translateResult.value = Resource.Error("Please select a target language.")
            return
        }

        viewModelScope.launch {
            _translateResult.value = Resource.Loading
            val result = translateRepository.translate(
                text = text,
                targetLang = targetLang.code
            )
            _translateResult.value = result
        }
    }
}
