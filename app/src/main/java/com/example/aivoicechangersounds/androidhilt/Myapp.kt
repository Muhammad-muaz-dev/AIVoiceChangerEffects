package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMiIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzcyNzI3OTQsImV4cCI6MTc3NzI3NDU5NH0.ebMOZpZurxiECbdMx33FwOcg2_dZaKbEkxOdH9rgrEc"
        )
    }
}