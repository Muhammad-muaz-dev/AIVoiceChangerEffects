package com.example.aivoicechangersounds.data.repository

import com.example.aivoicechangersounds.data.api.ApiService
import com.example.aivoicechangersounds.data.models.GenerateVoiceResponse
import com.example.aivoicechangersounds.data.models.Voice
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

    suspend fun getVoices(): Result<List<Voice>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getVoices()
            if (response.isSuccessful) {
                val voices = response.body()?.voices ?: emptyList()
                Result.success(voices)
            } else {
                Result.failure(Exception("Failed to fetch voices: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateVoice(
        voiceId: String,
        audioFilePath: String
    ): Result<GenerateVoiceResponse> = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                return@withContext Result.failure(Exception("Audio file not found"))
            }

            val voiceIdBody = voiceId.toRequestBody("text/plain".toMediaTypeOrNull())

            val audioRequestBody = audioFile.asRequestBody("audio/mpeg".toMediaTypeOrNull())
            val audioPart = MultipartBody.Part.createFormData(
                "audio_file",
                audioFile.name,
                audioRequestBody
            )

            val response = apiService.generateAudio(voiceIdBody, audioPart)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("Empty response from server"))
                }
            } else {
                Result.failure(Exception("Failed to generate voice: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}