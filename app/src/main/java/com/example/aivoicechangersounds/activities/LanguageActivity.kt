package com.example.aivoicechangersounds.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aivoicechangersounds.Viewmodels.VoiceAIViewModel
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.ui.voiceai.LanguageAdapter
import com.example.aivoicechangersounds.ui.voiceai.VoiceGridAdapter
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityLanguageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlin.collections.emptyList
import kotlin.getValue
@AndroidEntryPoint
class LanguageActivity : AppCompatActivity() {
    private val viewModel: VoiceAIViewModel by viewModels()
    private var languageList: List<Language> = emptyList()
    private lateinit var voiceAdapter: VoiceGridAdapter
    private lateinit var binding: ActivityLanguageBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding= ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val currentLang = viewModel.selectedLanguage.value

        val selectedIndex = languageList.indexOfFirst {
            it.code == currentLang?.code
        }.coerceAtLeast(0)

        val adapter = LanguageAdapter(languageList, selectedIndex) { selected ->
            voiceAdapter.submitList(emptyList())

            viewModel.selectLanguage(selected)


        }

        binding.rvLanguages.apply {
            layoutManager = LinearLayoutManager(this@LanguageActivity)
            this.adapter = adapter
        }

    }
}