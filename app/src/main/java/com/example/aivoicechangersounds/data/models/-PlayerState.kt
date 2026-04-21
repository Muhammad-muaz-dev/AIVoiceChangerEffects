package com.example.aivoicechangersounds.data.models


sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    object Ready : PlayerState()
    object Playing : PlayerState()
    object Paused : PlayerState()
    object Completed : PlayerState()
    data class Error(val message: String) : PlayerState()
}