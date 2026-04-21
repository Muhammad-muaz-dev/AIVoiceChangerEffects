package com.example.aivoicechangersounds.data.repository


import com.example.aivoicechangersounds.data.api.ApiServiceTTS
import com.example.aivoicechangersounds.data.models.GenerateAudioRequest
import com.example.aivoicechangersounds.data.models.GenerateAudioResponse
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.data.models.Voice
import com.example.aivoicechangersounds.utils.Resource
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VoiceRepository @Inject constructor(
    private val apiService: ApiServiceTTS
) {

    suspend fun getVoices(language: String?): Resource<List<Voice>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getVoices(language)

                if (response.isSuccessful && response.body() != null) {
                    Resource.Success(response.body()!!.voices)
                } else {
                    Resource.Error("Error: ${response.message()}")
                }

            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    suspend fun getLanguages(): Resource<List<Language>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getLanguages()

                if (response.isSuccessful && response.body() != null) {
                    Resource.Success(response.body()!!)
                } else {
                    Resource.Error("Error: ${response.message()}")
                }

            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    suspend fun generateAudio(
        text: String,
        voiceId: String,
        language: String
    ): Resource<GenerateAudioResponse> {

        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.generateTTS(
                    GenerateAudioRequest(text, voiceId, language)
                )

                if (response.isSuccessful && response.body() != null) {
                    Resource.Success(response.body()!!)
                } else {
                    Resource.Error("Error: ${response.message()}")
                }

            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }
}
