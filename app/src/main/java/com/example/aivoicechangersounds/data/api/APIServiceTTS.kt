package com.example.aivoicechangersounds.data.api

import com.example.aivoicechangersounds.data.models.GenerateAudioRequest
import com.example.aivoicechangersounds.data.models.GenerateAudioResponse
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.data.models.VoicesResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiServiceTTS {

    @GET("api/voices")
    suspend fun getVoices(
        @Query("language") language: String? = null
    ): Response<VoicesResponse>

    @GET("api/languages")
    suspend fun getLanguages(): Response<List<Language>>

    @POST("api/generate-audio")
    suspend fun generateTTS(
        @Body request: GenerateAudioRequest
    ): Response<GenerateAudioResponse>
}