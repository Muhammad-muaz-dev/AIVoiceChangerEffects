package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyNiIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3Nzc1MzQ0NjYsImV4cCI6MTc3NzUzNjI2Nn0.OvnzBhCK4VYUvCJoSgew8oB5N4Bhtd0M1U48b10OT9o"
        )
    }
}