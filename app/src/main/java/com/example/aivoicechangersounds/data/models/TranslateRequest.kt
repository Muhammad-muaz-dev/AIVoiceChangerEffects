package com.example.aivoicechangersounds.data.models
import com.google.gson.annotations.SerializedName

data class TranslateRequest(
    @SerializedName("text") val text: String,
    @SerializedName("target_lang") val targetLang: String
)
