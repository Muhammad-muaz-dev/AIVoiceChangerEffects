package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxOCIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzcyOTYxMjcsImV4cCI6MTc3NzI5NzkyN30.1A_vNwFZkJonhMWCZ4uV827t7xeEnP6WYBmLVi-HEoc"
        )
    }
}