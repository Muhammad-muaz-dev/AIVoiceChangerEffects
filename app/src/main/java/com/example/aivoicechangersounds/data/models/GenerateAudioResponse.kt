package com.example.aivoicechangersounds.data.models

import com.google.gson.annotations.SerializedName

data class GenerateAudioResponse(
    @SerializedName("audio_url") val audioUrl: String?,
    @SerializedName("audio_base64") val audioBase64: String?,
    @SerializedName("message") val message: String?
)