package com.example.aivoicechangersounds.data.models

import com.google.gson.annotations.SerializedName

data class Voice(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("language") val language: String?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("friendly_name") val friendlyName: String?
)