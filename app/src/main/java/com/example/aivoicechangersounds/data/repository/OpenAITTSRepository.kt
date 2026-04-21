package com.example.aivoicechangersounds.data.repository

import android.util.Log
import com.example.aivoicechangersounds.data.api.OpenAITTSService
import com.example.aivoicechangersounds.data.api.OpenAITTSRequest
import com.example.aivoicechangersounds.data.api.OpenAITTSResponse
import com.example.aivoicechangersounds.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAITTSRepository(private val apiService: OpenAITTSService) {
    
    suspend fun generateSpeech(text: String, voiceId: String = "alloy", language: String = "en"): Resource<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = OpenAITTSRequest(
                    input = text,
                    voice = voiceId
                )
                
                val response = apiService.synthesize("Bearer ${getApiKey()}", request)
                
                if (response.isSuccessful && response.body() != null) {
                    Log.d("OpenAITTSRepository", "API SUCCESS: ${response.code()}")
                    Resource.Success(response.body()!!.audioContent)
                } else {
                    Log.d("OpenAITTSRepository", "API ERROR: ${response.code()}, message: ${response.message()}")
                    Resource.Error("Failed to generate speech: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.d("OpenAITTSRepository", "API EXCEPTION: ${e.localizedMessage}")
                Resource.Error("Network error: ${e.localizedMessage}")
            }
        }
    }
    
    private fun getApiKey(): String {
        return try {
            val properties = java.util.Properties()
            properties.load(java.io.FileInputStream("local.properties"))
            val apiKey = properties.getProperty("OPENAI_API_KEY") ?: ""
            
            if (apiKey.isEmpty() || apiKey == "YOUR_OPENAI_API_KEY_HERE") {
                Log.d("OpenAITTSRepository", "OpenAI API key not configured. Please add your OpenAI API key to local.properties")
            }
            
            return apiKey
        } catch (e: Exception) {
            Log.d("OpenAITTSRepository", "Error reading API key: ${e.localizedMessage}")
            ""
        }
    }
}
