package com.example.aivoicechangersounds.activities

import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.aivoicechangersounds.data.models.RecordingState
import com.example.aivoicechangersounds.Viewmodels.RecordingViewModel
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityRecordingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.Manifest
import kotlin.jvm.java

@AndroidEntryPoint
class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private val viewModel: RecordingViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                viewModel.onAudioButtonClicked()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission is required to record audio",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnaudio.setOnClickListener {
            if (hasRecordPermission()) {
                viewModel.onAudioButtonClicked()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        binding.btncancel.setOnClickListener {
            viewModel.onCancelClicked()
        }

        binding.btndone.setOnClickListener {
            viewModel.onDoneClicked()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recordingState.collect { state ->
                        updateUI(state)
                    }
                }
                launch {
                    viewModel.formattedTime.collect { time ->
                        binding.tvTimer.text = time
                    }
                }
            }
        }
    }

    private fun updateUI(state: RecordingState) {
        when (state) {
            is RecordingState.Idle -> {
                binding.btnaudio.setImageResource(R.drawable.ic_audio)
                binding.btncancel.visibility = View.GONE
                binding.btndone.visibility = View.GONE
            }

            is RecordingState.Recording -> {
                binding.btnaudio.setImageResource(R.drawable.ic_pause)
                binding.btncancel.visibility = View.VISIBLE
                binding.btndone.visibility = View.VISIBLE
            }

            is RecordingState.Paused -> {
                binding.btnaudio.setImageResource(R.drawable.ic_play)
                binding.btncancel.visibility = View.VISIBLE
                binding.btndone.visibility = View.VISIBLE
            }

            is RecordingState.Done -> {
                navigateToNextScreen(state.filePath)
            }

            is RecordingState.Cancelled -> {
                binding.btnaudio.setImageResource(R.drawable.ic_audio)
                binding.btncancel.visibility = View.GONE
                binding.btndone.visibility = View.GONE
            }

            is RecordingState.Error -> {
                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                binding.btnaudio.setImageResource(R.drawable.ic_audio)
                binding.btncancel.visibility = View.GONE
                binding.btndone.visibility = View.GONE
            }
        }
    }

    private fun navigateToNextScreen(filePath: String) {
        // Replace VoiceEffectsActivity::class.java with your actual next Activity
        val intent = Intent(this, VoiceEffectActivity::class.java).apply {
            putExtra(EXTRA_AUDIO_FILE_PATH, filePath)
        }
        startActivity(intent)
        finish()
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_AUDIO_FILE_PATH = "extra_audio_file_path"
    }
}
