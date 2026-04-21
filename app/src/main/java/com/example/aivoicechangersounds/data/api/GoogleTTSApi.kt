package com.example.aivoicechangersounds.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class GoogleTTSRequest(
    val input: GoogleTTSInput,
    val voice: GoogleTTSVoice,
    val audioConfig: GoogleTTSAudioConfig
)

data class GoogleTTSInput(
    val text: String
)

data class GoogleTTSVoice(
    val languageCode: String = "en-US",
    val name: String = "en-US-Wavenet-D"
)

data class GoogleTTSAudioConfig(
    val audioEncoding: String = "MP3"
)

data class GoogleTTSResponse(
    val audioContent: String
)

interface GoogleTTSApiService {
    @POST("v1/text:synthesize")
    suspend fun synthesize(
        @Header("Authorization") authHeader: String,
        @Body request: GoogleTTSRequest
    ): Response<GoogleTTSResponse>
}
