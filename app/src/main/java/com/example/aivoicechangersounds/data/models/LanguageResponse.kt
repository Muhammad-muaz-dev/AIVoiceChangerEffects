package com.example.aivoicechangersounds.data.models

import com.google.gson.annotations.SerializedName

// LanguagesResponse.kt

data class LanguagesResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: LanguagesWrapper
)

data class LanguagesWrapper(
    @SerializedName("data") val languages: List<Language>
)
