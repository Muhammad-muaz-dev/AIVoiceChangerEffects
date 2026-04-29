package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyNCIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3Nzc0NTM0NTEsImV4cCI6MTc3NzQ1NTI1MX0.NtBlzf-TMQ7S-hJYd9aHGMPLZuyXzq5YxepzAM55LC4"
        )
    }
}