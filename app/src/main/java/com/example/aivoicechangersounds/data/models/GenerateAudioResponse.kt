package com.example.aivoicechangersounds.data.models

import com.google.gson.annotations.SerializedName

data class GenerateAudioResponse(
    @SerializedName("audio_url") val audioUrl: String?,
    @SerializedName("audio_base64") val audioBase64: String?,
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("duration") val duration: Long? = null
)
