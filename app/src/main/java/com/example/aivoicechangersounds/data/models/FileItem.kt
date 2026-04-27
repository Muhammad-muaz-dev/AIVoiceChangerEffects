package com.example.aivoicechangersounds.data.models
data class FileItem(
    val name: String,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val lastModified: Long,
    val category: FileCategory
)

enum class FileCategory {
    ALL,
    AI_VOICE,
    REVERSE,
    EFFECT,
    TRANSLATE
}
