package com.example.aivoicechangersounds.data.api.interfaces

import com.example.aivoicechangersounds.data.api.ApiUrl
import com.example.aivoicechangersounds.data.api.ApiUrls
import com.example.aivoicechangersounds.data.models.LanguagesResponse
import retrofit2.Response
import retrofit2.http.GET

interface ApiServiceLanguages {
    @GET("api/languages")
    suspend fun getLanguages(): Response<LanguagesResponse>

}