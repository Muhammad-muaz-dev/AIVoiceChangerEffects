package com.example.aivoicechangersounds.data.api.interfaces

import com.example.aivoicechangersounds.data.api.ApiUrl
import com.example.aivoicechangersounds.data.api.ApiUrls
import com.example.aivoicechangersounds.data.models.GenerateVoiceResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiServicesGenerateAudio {
    @Multipart
    @POST("api/generate-audio")
    suspend fun generateAudio(
        @Part("voice_id") voiceId: RequestBody,
        @Part audioFile: MultipartBody.Part
    ): Response<GenerateVoiceResponse>
}