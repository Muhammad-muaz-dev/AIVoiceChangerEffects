package com.example.aivoicechangersounds.data.api

import com.example.aivoicechangersounds.data.models.GenerateAudioRequest
import com.example.aivoicechangersounds.data.models.GenerateAudioResponse
import com.example.aivoicechangersounds.data.models.GenerateVoiceResponse
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.data.models.LanguagesResponse
import com.example.aivoicechangersounds.data.models.VoicesResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiServiceTTS {

    @GET("api/voices/free")
    suspend fun getVoices(
        @Query("language") language: String? = null
    ): Response<VoicesResponse>

    @GET("api/languages")
    suspend fun getLanguages(): Response<LanguagesResponse>

    @POST("api/generate-tts")
    suspend fun generateTTS(
        @Body request: GenerateAudioRequest
    ): Response<GenerateAudioResponse>

    @Multipart
    @POST("api/generate-audio")
    suspend fun generateAudio(
        @Part("voice_id") voiceId: RequestBody,
        @Part audioFile: MultipartBody.Part
    ): Response<GenerateVoiceResponse>
}