package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxOCIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzcyOTQxNjUsImV4cCI6MTc3NzI5NTk2NX0.s7EL0VOUDhwL7VX0UFRC5_OQks-5a8pIqelEBQh8MMc"
        )
    }
}