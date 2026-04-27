package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMiIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzcyODExMTIsImV4cCI6MTc3NzI4MjkxMn0.hR2lfR-or21QTi5WWoGrYVaQGBwXdMmomn6odj91AvA"
        )
    }
}