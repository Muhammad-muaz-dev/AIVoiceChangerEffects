package com.example.aivoicechangersounds.data.models

import com.google.gson.annotations.SerializedName


data class GenerateAudioRequest(
    @SerializedName("text") val text: String,
    @SerializedName("voice_id") val voiceId: String,
    @SerializedName("language") val language: String
)
