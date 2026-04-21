package com.example.aivoicechangersounds.ui.audioplayer


import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityAudioPlayerBinding
import com.voicechanger.app.databinding.DialogueSaveFileBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class AudioPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUDIO_URL = "extra_audio_url"
        const val EXTRA_AUDIO_BASE64 = "extra_audio_base64"
        const val EXTRA_VOICE_NAME = "extra_voice_name"
        const val EXTRA_INPUT_TEXT = "extra_input_text"
    }

    private lateinit var binding: ActivityAudioPlayerBinding
    private val viewModel: AudioPlayerViewModel by viewModels()

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            viewModel.updateProgress()
            progressHandler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadAudioData()
        setupControls()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbaraudioplayer)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Audio Player"
        binding.toolbaraudioplayer.setNavigationOnClickListener { finish() }
    }

    private fun loadAudioData() {
        val voiceName = intent.getStringExtra(EXTRA_VOICE_NAME) ?: ""
        val inputText = intent.getStringExtra(EXTRA_INPUT_TEXT) ?: ""
        binding.textViewVoiceName.text = voiceName
        binding.textViewVoiceId.text = inputText

        val audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
        val audioBase64 = intent.getStringExtra(EXTRA_AUDIO_BASE64)

        when {
            !audioUrl.isNullOrBlank() -> viewModel.initFromUrl(audioUrl)
            !audioBase64.isNullOrBlank() -> viewModel.initFromBase64(audioBase64, cacheDir)
            else -> {
                Toast.makeText(this, "No audio data available", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupControls() {
        binding.buttonReplay.setOnClickListener {
            when (viewModel.playerState.value) {
                is PlayerState.Playing -> viewModel.pause()
                is PlayerState.Paused,
                is PlayerState.Ready,
                is PlayerState.Completed -> viewModel.play()
                else -> {}
            }
        }

        binding.buttonReplay.setOnClickListener {
            viewModel.replay()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.seekTo(progress)
                    binding.textViewCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnsave.setOnClickListener {
            showSaveFileDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.playerState.observe(this) { state ->
            when (state) {
                is PlayerState.Ready -> {
                    binding.buttonReplay
                    viewModel.play()
                }
                is PlayerState.Playing -> {
                    binding.buttonReplay.setImageResource(R.drawable.ic_pause)
                    startProgressUpdates()
                }
                is PlayerState.Paused -> {
                    binding.buttonReplay.setImageResource(R.drawable.ic_playbutton)
                    stopProgressUpdates()
                }
                is PlayerState.Completed -> {
                    binding.buttonReplay.setImageResource(R.drawable.ic_playbutton)
                    stopProgressUpdates()
                }
                is PlayerState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    stopProgressUpdates()
                }
                else -> {
                    stopProgressUpdates()
                }
            }
        }

        viewModel.duration.observe(this) { durationMs ->
            binding.seekBar.max = durationMs
            binding.textViewTotalTime.text = formatTime(durationMs)
        }

        viewModel.progress.observe(this) { progressMs ->
            binding.seekBar.progress = progressMs
            binding.textViewCurrentTime.text = formatTime(progressMs)
        }
    }

    private fun startProgressUpdates() {
        progressHandler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
    }

    private fun formatTime(millis: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis.toLong()) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onPause() {
        super.onPause()
        stopProgressUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.playerState.value is PlayerState.Playing) {
            startProgressUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdates()
    }

    private fun showSaveFileDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = DialogueSaveFileBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val timestamp = System.currentTimeMillis()
        val filename = "AUD-$timestamp"

        sheetBinding.btnDone.setOnClickListener {
            saveAudioFile(filename)
            dialog.dismiss()
        }

        sheetBinding.btnShare.setOnClickListener {
            shareAudioFile(filename)
        }

        dialog.show()
    }

    private fun saveAudioFile(filename: String) {
        Toast.makeText(this, "Audio saved as $filename", Toast.LENGTH_SHORT).show()
    }

    private fun shareAudioFile(filename: String) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(android.content.Intent.EXTRA_TEXT, "Check out my voice effect!")
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "Share via"))
    }
}