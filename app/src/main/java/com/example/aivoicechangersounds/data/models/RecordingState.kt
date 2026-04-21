package com.example.aivoicechangersounds.data.models

sealed class RecordingState {
    object Idle : RecordingState()
    object Recording : RecordingState()
    object Paused : RecordingState()
    data class Done(val filePath: String) : RecordingState()
    object Cancelled : RecordingState()
    data class Error(val message: String) : RecordingState()
}