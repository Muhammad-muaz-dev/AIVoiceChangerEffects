package com.example.aivoicechangersounds.data.repository


import com.example.aivoicechangersounds.data.api.ApiService
import com.example.aivoicechangersounds.data.models.GenerateAudioRequest
import com.example.aivoicechangersounds.data.models.GenerateAudioResponse
import com.example.aivoicechangersounds.data.models.Voice
import com.example.aivoicechangersounds.utils.Resource

class VoiceRepository(private val apiService: ApiService) {

    suspend fun getVoices(language: String? = null): Resource<List<Voice>> {
        return try {
            val response = apiService.getVoices(language)
            if (response.isSuccessful) {
                val voices = response.body()?.voices ?: emptyList()
                Resource.Success(voices)
            } else {
                Resource.Error("Failed to load voices: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }

    suspend fun generateAudio(
        text: String,
        voiceId: String,
        language: String
    ): Resource<GenerateAudioResponse> {
        return try {
            val request = GenerateAudioRequest(text, voiceId, language)
            val response = apiService.generateAudio(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Resource.Success(body)
                } else {
                    Resource.Error("Empty response from server")
                }
            } else {
                Resource.Error("Generation failed: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error("Network error: ${e.localizedMessage}")
        }
    }
}
