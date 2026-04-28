package com.example.aivoicechangersounds.data.repository

import android.util.Log
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceVoices
import com.example.aivoicechangersounds.data.api.interfaces.ApiServicesGenerateAudio
import com.example.aivoicechangersounds.data.models.GenerateVoiceResponse
import com.example.aivoicechangersounds.data.models.Voice
import com.example.aivoicechangersounds.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceEffectRepository @Inject constructor(
    private val api: ApiServiceVoices
) {

    // ─────────────────────────────────────────────
    // GET VOICES
    // ─────────────────────────────────────────────
    suspend fun getVoices(language: String?): Resource<List<Voice>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getVoices(language)

                Log.d("API_DEBUG", "Voices Response Code: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()

                    if (body != null) {
                        val voices = body.data.voices

                        Log.d("API_DEBUG", "Voices size: ${voices.size}")

                        Resource.Success(voices)
                    } else {
                        Resource.Error("Empty response body")
                    }

                } else {
                    val error = response.errorBody()?.string()

                    Log.e("API_ERROR", "Voices Error: $error")

                    Resource.Error("Error ${response.code()}: ${response.message()}")
                }

            } catch (e: Exception) {
                Log.e("API_EXCEPTION", "Voices Exception: ${e.message}", e)
                Resource.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }


}