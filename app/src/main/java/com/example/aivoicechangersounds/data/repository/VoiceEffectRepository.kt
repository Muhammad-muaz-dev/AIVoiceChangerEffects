package com.example.aivoicechangersounds.data.repository

import android.util.Log
import com.example.aivoicechangersounds.data.api.ApiService
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
    private val apiService: ApiService
) {

    suspend fun getVoices(language: String?): Resource<List<Voice>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getVoices(language)
                Log.d("Voices coming", "getVoices: $response")
                if (response.isSuccessful && response.body() != null) {

                    val voices = response.body()!!.data.voices

                    Log.d("VOICE_DEBUG", "Voices size: ${voices.size}")

                    Resource.Success(voices)

                } else {
                    Resource.Error("Error: ${response.message()}")
                }

            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    suspend fun generateVoice(
        voiceId: String,
        audioFilePath: String
    ): Resource<GenerateVoiceResponse> = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(audioFilePath)

            if (!audioFile.exists()) {
                return@withContext Resource.Error("Audio file not found")
            }

            val voiceIdBody = voiceId.toRequestBody("text/plain".toMediaTypeOrNull())

            val audioRequestBody = audioFile.asRequestBody("audio/mpeg".toMediaTypeOrNull())
            val audioPart = MultipartBody.Part.createFormData(
                "audio_file",
                audioFile.name,
                audioRequestBody
            )

            val response = apiService.generateAudio(voiceIdBody, audioPart)

            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Failed: ${response.code()} ${response.message()}")
            }

        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Unknown error")
        }
    }
}