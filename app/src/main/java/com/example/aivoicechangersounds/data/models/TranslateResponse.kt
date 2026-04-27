package com.example.aivoicechangersounds.data.models

import com.google.gson.annotations.SerializedName

data class TranslateResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: TranslateData
)

data class TranslateData(
    @SerializedName("translated_text") val translatedText: String
)
