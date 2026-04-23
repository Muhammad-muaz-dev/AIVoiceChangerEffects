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
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyIiwidHlwZSI6ImFjY2VzcyIsImlhdCI6MTc3NjkxOTIxOCwiZXhwIjoxNzc2OTIxMDE4fQ.i1ilW_lyfpHMvFB01OfNA_BVhZSINeH7znHigNFkvMk"
        )
    }
}