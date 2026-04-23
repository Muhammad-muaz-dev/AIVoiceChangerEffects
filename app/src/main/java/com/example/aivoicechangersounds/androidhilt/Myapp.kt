package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        // TODO: Replace with real login flow later
        // For now set the token once on app start for testing
        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMSIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzY5NDY4NzksImV4cCI6MTc3Njk0ODY3OX0.8G6AN-gqwi_QO2X2rMN2YqOOg4wYr0FHUONRYJDWcWc"
        )
    }
}