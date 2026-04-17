package com.example.aivoicechangersounds.data.models

import com.example.aivoicechangersounds.data.models.Voice
import com.google.gson.annotations.SerializedName

data class VoicesResponse(
    @SerializedName("voices") val voices: List<Voice>
)