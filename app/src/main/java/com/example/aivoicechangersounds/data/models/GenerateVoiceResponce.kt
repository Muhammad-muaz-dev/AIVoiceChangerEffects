package com.example.aivoicechangersounds.data.models

import com.google.gson.annotations.SerializedName


data class GenerateVoiceResponse(
    @SerializedName("audio_url")
    val audioUrl: String? = null,

    @SerializedName("audio_base64")
    val audioBase64: String? = null,

    @SerializedName("voice_name")
    val voiceName: String? = null
)