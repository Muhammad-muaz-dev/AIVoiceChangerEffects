package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyMyIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzczODAwNzEsImV4cCI6MTc3NzM4MTg3MX0.1zMbhwWTigRV8rKnxLZD7SIzb34wxi2Sxv78F_-rHR4"
        )
    }
}