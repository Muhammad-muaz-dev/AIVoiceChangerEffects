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
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMSIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzY5NDQ0MjEsImV4cCI6MTc3Njk0NjIyMX0.xfT4oKK5Wqz5XiKVH9bpf7-XK5sgrUgKrmmh3IIAYyo"
        )
    }
}