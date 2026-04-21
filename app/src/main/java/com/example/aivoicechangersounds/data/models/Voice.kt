package com.example.aivoicechangersounds.data.models

import com.google.gson.annotations.SerializedName

data class Voice(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("preview_url") val previewUrl: String?,
    @SerializedName("gender") val gender: String?
) {
    // Constructor for Google TTS voices
    constructor(id: String, name: String, gender: String, language: String) : this(
        id = id,
        name = name,
        avatarUrl = null,
        language = language,
        previewUrl = null,
        gender = gender
    )
}