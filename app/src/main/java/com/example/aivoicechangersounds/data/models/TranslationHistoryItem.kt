package com.example.aivoicechangersounds.data.models

data class TranslationHistoryItem(
    val sourceLanguage: String,
    val sourceText: String,
    val targetLanguage: String,
    val translatedText: String
)
