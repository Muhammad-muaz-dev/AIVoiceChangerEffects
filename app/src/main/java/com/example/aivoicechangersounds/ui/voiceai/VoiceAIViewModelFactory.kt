package com.example.aivoicechangersounds.ui.voiceai


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aivoicechangersounds.data.repository.VoiceRepository

class VoiceAIViewModelFactory(private val repository: VoiceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoiceAIViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VoiceAIViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
