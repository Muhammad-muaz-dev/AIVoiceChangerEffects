package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyNiIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3Nzc0Njg4NDQsImV4cCI6MTc3NzQ3MDY0NH0.7uFxQgCTBKKLCDrVWcoyhbahbfzcRJrrdBdiCoRMhbg"
        )
    }
}