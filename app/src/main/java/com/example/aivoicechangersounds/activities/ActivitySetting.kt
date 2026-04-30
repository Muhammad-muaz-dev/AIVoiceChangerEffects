package com.example.aivoicechangersounds.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityPreviewBinding
import com.voicechanger.app.databinding.ActivitySettingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ActivitySetting : AppCompatActivity() {
    private lateinit var binding: ActivitySettingBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setUpToolbar()
        goToLanguageChane()
    }
    private fun setUpToolbar(){
        binding.btnback.setOnClickListener { finish() }
    }
    private fun goToLanguageChane(){
        binding.linearlanguage.setOnClickListener {
            val intent= Intent(this@ActivitySetting, LanguageActivity::class.java)
            startActivity(intent)
        }
    }

}