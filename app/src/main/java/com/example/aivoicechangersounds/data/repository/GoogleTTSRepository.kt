package com.example.aivoicechangersounds.data.repository

import android.util.Log
import com.example.aivoicechangersounds.data.api.GoogleTTSApiService
import com.example.aivoicechangersounds.data.api.GoogleTTSAudioConfig
import com.example.aivoicechangersounds.data.api.GoogleTTSInput
import com.example.aivoicechangersounds.data.api.GoogleTTSRequest
import com.example.aivoicechangersounds.data.api.GoogleTTSResponse
import com.example.aivoicechangersounds.data.api.GoogleTTSVoice
import com.example.aivoicechangersounds.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleTTSRepository(private val apiService: GoogleTTSApiService) {
    
    suspend fun generateSpeech(text: String, voiceId: String, language: String = "en-US"): Resource<GoogleTTSResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("GoogleTTSRepository", "API call: text='$text', voiceId='$voiceId', language='$language'")
                val request = GoogleTTSRequest(
                    input = GoogleTTSInput(text = text),
                    voice = GoogleTTSVoice(languageCode = language, name = voiceId),
                    audioConfig = GoogleTTSAudioConfig()
                )
                
                val response = apiService.synthesize("Bearer ${getApiKey()}", request)
                
                if (response.isSuccessful && response.body() != null) {
                    Log.d("GoogleTTSRepository", "API SUCCESS: ${response.code()}")
                    Resource.Success(response.body()!!)
                } else {
                    Log.d("GoogleTTSRepository", "API ERROR: ${response.code()}, message: ${response.message()}")
                    Resource.Error("Failed to generate speech: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.d("GoogleTTSRepository", "API EXCEPTION: ${e.localizedMessage}")
                Resource.Error("Network error: ${e.localizedMessage}")
            }
        }
    }
    
    private fun getApiKey(): String {
        return try {
            val properties = java.util.Properties()
            properties.load(java.io.FileInputStream("local.properties"))
            val apiKey = properties.getProperty("GOOGLE_TTS_API_KEY") ?: ""
            
            if (apiKey.isEmpty() || apiKey == "YOUR_ACTUAL_GOOGLE_TTS_API_KEY_HERE") {
                Log.d("GoogleTTSRepository", "API key not configured. Please add your Google TTS API key to local.properties")
            }
            
            return apiKey
        } catch (e: Exception) {
            Log.d("GoogleTTSRepository", "Error reading API key: ${e.localizedMessage}")
            ""
        }
    }
}
