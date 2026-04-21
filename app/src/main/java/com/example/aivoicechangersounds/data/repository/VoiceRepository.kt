package com.example.aivoicechangersounds.data.repository


import com.example.aivoicechangersounds.data.api.ApiService
import com.example.aivoicechangersounds.data.models.GenerateAudioRequest
import com.example.aivoicechangersounds.data.models.GenerateAudioResponse
import com.example.aivoicechangersounds.data.models.Voice
import com.example.aivoicechangersounds.utils.Resource

class VoiceRepository(private val apiService: ApiService) {

    suspend fun getVoices(language: String? = null): Resource<List<Voice>> {
        return try {
            // Use OpenAI TTS voices
            val voices = getOpenAIVOICES(language ?: "en-US")
            Resource.Success(voices)
        } catch (e: Exception) {
            Resource.Error("Error loading voices: ${e.localizedMessage}")
        }
    }
    
    private fun getOpenAIVOICES(language: String): List<Voice> {
        return when (language) {
            "en", "en-US" -> listOf(
                Voice("alloy", "Alloy", "", "en-US"),
                Voice("echo", "Echo", "", "en-US"),
                Voice("fable", "Fable", "", "en-US"),
                Voice("onyx", "Onyx", "", "en-US"),
                Voice("nova", "Nova", "", "en-US"),
                Voice("shimmer", "Shimmer", "", "en-US")
            )
            "es", "es-ES" -> listOf(
                Voice("alloy", "Alloy", "", "es-ES"),
                Voice("echo", "Echo", "", "es-ES"),
                Voice("fable", "Fable", "", "es-ES"),
                Voice("onyx", "Onyx", "", "es-ES"),
                Voice("nova", "Nova", "", "es-ES")
            )
            "fr", "fr-FR" -> listOf(
                Voice("alloy", "Alloy", "", "fr-FR"),
                Voice("echo", "Echo", "", "fr-FR"),
                Voice("fable", "Fable", "", "fr-FR")
            )
            else -> listOf(
                Voice("alloy", "Alloy", "", "en-US"),
                Voice("echo", "Echo", "", "en-US"),
                Voice("fable", "Fable", "", "en-US"),
                Voice("onyx", "Onyx", "", "en-US"),
                Voice("nova", "Nova", "", "en-US")
            )
        }
    }
    
    private fun getGoogleTTSVoices(language: String): List<Voice> {
        return when (language) {
            "en-US", "en" -> listOf(
                Voice("en-US-Wavenet-A", "Aria", "Female", "en-US"),
                Voice("en-US-Wavenet-B", "Ben", "Male", "en-US"),
                Voice("en-US-Wavenet-C", "Clara", "Female", "en-US"),
                Voice("en-US-Wavenet-D", "David", "Male", "en-US"),
                Voice("en-US-Wavenet-E", "Eva", "Female", "en-US"),
                Voice("en-US-Wavenet-F", "Freya", "Female", "en-US"),
                Voice("en-US-Standard-A", "Anna", "Female", "en-US"),
                Voice("en-US-Standard-B", "Brian", "Male", "en-US"),
                Voice("en-US-Standard-C", "Carla", "Female", "en-US"),
                Voice("en-US-Standard-D", "Daniel", "Male", "en-US")
            )
            "es-ES", "es" -> listOf(
                Voice("es-ES-Wavenet-A", "Ana", "Female", "es-ES"),
                Voice("es-ES-Wavenet-B", "Berto", "Male", "es-ES"),
                Voice("es-ES-Standard-A", "Elvira", "Female", "es-ES"),
                Voice("es-ES-Standard-B", "Jorge", "Male", "es-ES")
            )
            "fr-FR", "fr" -> listOf(
                Voice("fr-FR-Wavenet-A", "Ariane", "Female", "fr-FR"),
                Voice("fr-FR-Wavenet-B", "Bertrand", "Male", "fr-FR"),
                Voice("fr-FR-Standard-A", "Celine", "Female", "fr-FR"),
                Voice("fr-FR-Standard-B", "Denis", "Male", "fr-FR")
            )
            "de-DE", "de" -> listOf(
                Voice("de-DE-Wavenet-A", "Anna", "Female", "de-DE"),
                Voice("de-DE-Wavenet-B", "Bernd", "Male", "de-DE"),
                Voice("de-DE-Standard-A", "Katja", "Female", "de-DE"),
                Voice("de-DE-Standard-B", "Klaus", "Male", "de-DE")
            )
            else -> listOf(
                Voice("en-US-Wavenet-A", "Aria", "Female", "en-US"),
                Voice("en-US-Wavenet-B", "Ben", "Male", "en-US"),
                Voice("en-US-Wavenet-C", "Clara", "Female", "en-US"),
                Voice("en-US-Wavenet-D", "David", "Male", "en-US")
            )
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
