package com.example.aivoicechangersounds.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.example.aivoicechangersounds.data.models.VoiceAIMode
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlin.jvm.java

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupClickListeners()
        setupKebabMenu()
    }

    private fun setupClickListeners() {
        binding.cardvoicechange.setOnClickListener {
            val intent = Intent(this, VoiceAIActivity::class.java)
            intent.putExtra("mode", VoiceAIMode.TEXT_TO_SPEECH.name)
            startActivity(intent)
        }

        binding.cardttv.setOnClickListener {
            val intent = Intent(this, VoiceAIActivity::class.java)
            intent.putExtra("mode", VoiceAIMode.TRANSLATE.name)
            startActivity(intent)
        }

        binding.cardrav.setOnClickListener {
            startActivity(Intent(this, RecordingActivity::class.java))
        }
        binding.cardrv.setOnClickListener {
            startActivity(Intent(this, ReverseVoice::class.java))
        }
        binding.cardvoicet.setOnClickListener {
            startActivity(Intent(this, ActivityVoiceTranslate::class.java))
        }
        binding.cardfile.setOnClickListener {
            startActivity(Intent(this, ActivityFile::class.java))
        }
        binding.cardve.setOnClickListener {
            val intent = Intent(this, VoiceAIActivity::class.java)
            intent.putExtra("mode", VoiceAIMode.VOICE_CLONE.name)
            startActivity(intent)
        }

    }
    private fun setupKebabMenu() {
        binding.menuhome.setOnClickListener { view ->

            val popupMenu = PopupMenu(this, view)
            popupMenu.menuInflater.inflate(R.menu.menu_home_kebab, popupMenu.menu)

            try {
                val field = popupMenu.javaClass.getDeclaredField("mPopup")
                field.isAccessible = true
                val menuPopupHelper = field.get(popupMenu)
                val method = menuPopupHelper.javaClass
                    .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                method.invoke(menuPopupHelper, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_rename -> {
                        navigateToSettingScreen()
                        true
                    }

                    else -> false
                }
            }

            popupMenu.show()
        }
    }
    private fun navigateToSettingScreen(){
        val intent= Intent(this@MainActivity, ActivitySetting::class.java)
        startActivity(intent)
    }
}