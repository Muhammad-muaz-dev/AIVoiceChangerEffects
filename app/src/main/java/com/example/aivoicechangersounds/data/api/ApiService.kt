package com.example.aivoicechangersounds.data.api

import com.example.aivoicechangersounds.data.models.GenerateVoiceResponse
import com.example.aivoicechangersounds.data.models.LanguagesResponse
import com.example.aivoicechangersounds.data.models.VoicesResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Handles:
 *  - GET  api/voices/free      – fetch available voices (optionally filtered by language)
 *  - GET  api/languages        – fetch available languages
 *  - POST api/generate-audio   – speech-to-speech (upload audio, get converted audio back)
 */
@ApiUrl(ApiUrls.MAIN_BASE_URL)
interface ApiService {

    @GET("api/voices/free")
    suspend fun getVoices(
        @Query("language") language: String? = null,
        @Header("Authorization") token: String = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyIiwidHlwZSI6ImFjY2VzcyIsImlhdCI6MTc3Njg0MTA4MSwiZXhwIjoxNzc2ODQyODgxfQ.UZjIhbnoC1xCWUSgsKtFRqUljaarHrsZN-CKQFa3oDc"
    ): Response<VoicesResponse>

    @GET("api/languages")
    suspend fun getLanguages(): Response<LanguagesResponse>

    @Multipart
    @POST("api/generate-audio")
    suspend fun generateAudio(
        @Part("voice_id") voiceId: RequestBody,
        @Part audioFile: MultipartBody.Part
    ): Response<GenerateVoiceResponse>
}