package com.example.aivoicechangersounds.data.models

import com.google.gson.annotations.SerializedName


data class
GenerateAudioRequest(
    @SerializedName("text") val text: String,
    @SerializedName("model") val model: String
)
