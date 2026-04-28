package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyMyIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzczNjA3MTIsImV4cCI6MTc3NzM2MjUxMn0.nmnm5i0S0zpnAs90AVfzaQiyyGtlpTbM8u-CfSPT84E"
        )
    }
}