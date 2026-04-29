package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyNCIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3Nzc0NDEyNjQsImV4cCI6MTc3NzQ0MzA2NH0.oiS5bQMYVgdnpADBp4giGjrTDFZAgLlXD2fHMoNHt-U"
        )
    }
}