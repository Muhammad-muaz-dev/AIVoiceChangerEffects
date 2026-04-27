package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMiIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzcyNzAxMjQsImV4cCI6MTc3NzI3MTkyNH0.bJHBC2EC0wPJTG4gd8JngJtAgiJ0Gqa3hRpwB8uY5jM"
        )
    }
}