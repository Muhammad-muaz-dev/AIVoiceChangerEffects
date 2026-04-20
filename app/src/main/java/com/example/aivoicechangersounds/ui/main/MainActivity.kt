package com.example.aivoicechangersounds.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aivoicechangersounds.ui.voiceai.VoiceAIActivity
import com.voicechanger.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.cardvoicechange.setOnClickListener {
            startActivity(Intent(this, VoiceAIActivity::class.java))
        }

        binding.cardttv.setOnClickListener {
            // TODO: Implement Text to Voice screen
        }

        binding.cardrav.setOnClickListener {
            // TODO: Implement Record Audio screen
        }
    }
}