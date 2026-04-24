package com.example.aivoicechangersounds.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.voicechanger.app.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ActivityVoiceTranslate : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_voice_translate)

    }
}