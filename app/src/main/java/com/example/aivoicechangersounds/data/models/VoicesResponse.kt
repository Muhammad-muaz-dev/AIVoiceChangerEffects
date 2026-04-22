package com.example.aivoicechangersounds.data.models

import com.example.aivoicechangersounds.data.models.Voice
import com.google.gson.annotations.SerializedName

data class VoicesResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: VoicesWrapper
)
data class VoicesWrapper(
    @SerializedName("data") val voices: List<Voice>
)