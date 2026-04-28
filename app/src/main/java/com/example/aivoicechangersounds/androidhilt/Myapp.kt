package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyMCIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzczNTYxNTAsImV4cCI6MTc3NzM1Nzk1MH0.JXPeRk0lXPi2oDy2BQqnKBgrjL2HtC_e4_L9rgIzQ6g"
        )
    }
}