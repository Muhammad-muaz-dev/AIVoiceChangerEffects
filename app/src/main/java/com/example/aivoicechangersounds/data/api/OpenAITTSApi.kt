package com.example.aivoicechangersounds.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class OpenAITTSRequest(
    val model: String = "tts-1",
    val input: String,
    val voice: String? = null,
    val response_format: String = "mp3"
)

data class OpenAITTSResponse(
    val audioContent: String
)

interface OpenAITTSService {
    @POST("v1/audio/speech")
    suspend fun synthesize(
        @Header("Authorization") authorization: String,
        @Body request: OpenAITTSRequest
    ): Response<OpenAITTSResponse>
}
