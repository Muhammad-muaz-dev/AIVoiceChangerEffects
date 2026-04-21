package com.example.aivoicechangersounds.ui.voiceai


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aivoicechangersounds.data.repository.OpenAITTSRepository
import com.example.aivoicechangersounds.data.repository.VoiceRepository

class VoiceAIViewModelFactory(
    private val voiceRepository: VoiceRepository,
    private val openAITTSRepository: OpenAITTSRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoiceAIViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VoiceAIViewModel(voiceRepository, openAITTSRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
