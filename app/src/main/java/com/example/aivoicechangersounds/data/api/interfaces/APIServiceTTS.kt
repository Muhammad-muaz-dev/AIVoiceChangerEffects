package com.example.aivoicechangersounds.data.api.interfaces

import com.example.aivoicechangersounds.data.models.GenerateAudioRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * Handles:
 *  - GET  api/voices/free    – fetch available voices
 *  - GET  api/languages      – fetch available languages
 *  - POST api/generate       – text-to-speech
 *  - POST api/generate-audio – speech-to-speech (multipart)
 *
 * Token is NOT added here. ServiceFactory's OkHttp interceptor
 * attaches "Authorization: Bearer ..." to every request automatically.
 */

interface ApiServiceTTS {

    @Streaming
    @POST("api/generate")
    suspend fun generateTTS(
        @Body request: GenerateAudioRequest
    ): Response<ResponseBody>

}