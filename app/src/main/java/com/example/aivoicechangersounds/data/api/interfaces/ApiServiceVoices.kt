package com.example.aivoicechangersounds.data.api.interfaces

import com.example.aivoicechangersounds.data.api.ApiUrl
import com.example.aivoicechangersounds.data.api.ApiUrls
import com.example.aivoicechangersounds.data.models.GenerateVoiceResponse
import com.example.aivoicechangersounds.data.models.LanguagesResponse
import com.example.aivoicechangersounds.data.models.VoicesResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Handles:
 *  - GET  api/voices/free    – fetch available voices (filtered by language)
 *  - GET  api/languages      – fetch available languages
 *  - POST api/generate-audio – speech-to-speech (upload audio file)
 *
 * Token is NOT added here. ServiceFactory's OkHttp interceptor
 * attaches "Authorization: Bearer ..." to every request automatically.
 */

interface ApiServiceVoices {

    @GET("api/voices/free")
    suspend fun getVoices(
        @Query("language") language: String? = null
    ): Response<VoicesResponse>



}