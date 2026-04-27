package com.example.aivoicechangersounds.data.api.interfaces

import com.example.aivoicechangersounds.data.models.TranslateRequest
import com.example.aivoicechangersounds.data.models.TranslateResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiServiceTranslate {

    @POST("api/translate")
    suspend fun translate(
        @Body request: TranslateRequest
    ): Response<TranslateResponse>
}
