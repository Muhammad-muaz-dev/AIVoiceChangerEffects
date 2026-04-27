package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxOSIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzcyOTk0NTIsImV4cCI6MTc3NzMwMTI1Mn0.bjUhrA4wAkiWjUqtY_YmpfGzttgoTVSWtVu0gWq-E50"
        )
    }
}