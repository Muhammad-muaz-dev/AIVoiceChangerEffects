package com.example.aivoicechangersounds.androidhilt

import android.app.Application
import com.example.aivoicechangersounds.data.api.TokenProvider
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Myapp: Application() {
    override fun onCreate() {
        super.onCreate()

        TokenProvider.setToken(
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxOCIsInR5cGUiOiJhY2Nlc3MiLCJpYXQiOjE3NzcyOTgyNDIsImV4cCI6MTc3NzMwMDA0Mn0.-6oScceILCcf_C6Yp9a3jsnKBaa0b457prBcBMQhhxQ"
        )
    }
}