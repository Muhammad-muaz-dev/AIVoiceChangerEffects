package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMiIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzcwMTY3MjEsImV4cCI6MTc3NzAxODUyMX0.irb6Id4Za6UZuxpnlG1qoFIikbpjoeo7RrU79v6K5dI"
        )
    }
}