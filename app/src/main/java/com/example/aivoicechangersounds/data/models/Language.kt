package com.example.aivoicechangersounds.data.models

import com.google.gson.annotations.SerializedName

data class Language(
    @SerializedName ("code") val code: String,
    @SerializedName ("name") val displayName: String
)