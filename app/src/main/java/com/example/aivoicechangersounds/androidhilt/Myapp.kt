package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMSIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzcwMDM4NTQsImV4cCI6MTc3NzAwNTY1NH0.jI2OXBKEmHKNSuCLnlJsuBUfrPhlaLmv08qZVbn66YQ"
        )
    }
}