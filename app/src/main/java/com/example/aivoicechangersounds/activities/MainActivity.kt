package com.example.aivoicechangersounds.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.voicechanger.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
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
           startActivity(Intent(this, VoiceAIActivity::class.java))
        }

        binding.cardrav.setOnClickListener {
            startActivity(Intent(this, RecordingActivity::class.java))
        }
    }
}