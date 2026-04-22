package com.example.aivoicechangersounds.data.repository

import android.content.Context
import android.util.Log
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceLanguages
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceTTS
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceVoices
import com.example.aivoicechangersounds.data.models.GenerateAudioRequest
import com.example.aivoicechangersounds.data.models.GenerateAudioResponse
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.data.models.Voice
import com.example.aivoicechangersounds.utils.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import javax.inject.Inject

class VoiceRepository @Inject constructor(
    private val apiTTS: ApiServiceTTS,
    private val apiVoices: ApiServiceVoices,
    private val apiLanguages: ApiServiceLanguages,
    @ApplicationContext private val context: Context
) {

    // ───────────────────────── Voices ─────────────────────────
    suspend fun getVoices(language: String?): Resource<List<Voice>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiVoices.getVoices(language)

                if (response.isSuccessful && response.body() != null) {
                    val voices = response.body()!!.data.voices
                    Log.d("VOICE_DEBUG", "Voices size: ${voices.size}")
                    Resource.Success(voices)
                } else {
                    Resource.Error("Error: ${response.code()} ${response.message()}")
                }

            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    // ───────────────────────── Languages ─────────────────────────
    suspend fun getLanguages(): Resource<List<Language>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiLanguages.getLanguages()

                if (response.isSuccessful && response.body() != null) {
                    val languages = response.body()!!.data.languages
                    Log.d("VOICE_DEBUG", "Languages size: ${languages.size}")
                    Resource.Success(languages)
                } else {
                    Resource.Error("Error: ${response.code()} ${response.message()}")
                }

            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    // ───────────────────────── TTS AUDIO (FIXED FOR BINARY STREAM) ─────────────────────────
    suspend fun generateAudio(
        text: String,
        model: String
    ): Resource<GenerateAudioResponse> {

        return withContext(Dispatchers.IO) {
            try {
                val response = apiTTS.generateTTS(
                    GenerateAudioRequest(text, model)
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    
                    // Save the binary stream to a file
                    val fileName = "tts_${System.currentTimeMillis()}.mp3"
                    val file = File(context.cacheDir, fileName)
                    
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    Log.d("VOICE_DEBUG", "File saved at: ${file.absolutePath}")
                    Resource.Success(GenerateAudioResponse(filePath = file.absolutePath, audioUrl = null, audioBase64 = null))
                } else {
                    Resource.Error("${response.code()} ${response.message()}")
                }

            } catch (e: Exception) {
                Log.e("VOICE_DEBUG", "Error generating audio", e)
                Resource.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }
}