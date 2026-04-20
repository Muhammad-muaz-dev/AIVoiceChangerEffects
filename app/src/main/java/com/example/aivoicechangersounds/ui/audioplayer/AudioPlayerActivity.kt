package com.example.aivoicechangersounds.ui.audioplayer


import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.voicechanger.app.databinding.ActivityAudioPlayerBinding
import java.util.concurrent.TimeUnit
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.voicechanger.app.databinding.DialogueSaveFileBinding
import com.example.aivoicechangersounds.ui.audioplayer.PlayerState
import com.example.aivoicechangersounds.ui.audioplayer.AudioPlayerViewModel

class AudioPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUDIO_URL = "extra_audio_url"
        const val EXTRA_AUDIO_BASE64 = "extra_audio_base64"
        const val EXTRA_VOICE_NAME = "extra_voice_name"
        const val EXTRA_INPUT_TEXT = "extra_input_text"
    }

    private lateinit var binding: ActivityAudioPlayerBinding
    private lateinit var viewModel: AudioPlayerViewModel

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
        setupViewModel()
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

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[AudioPlayerViewModel::class.java]
    }

    private fun loadAudioData() {
        val voiceName = intent.getStringExtra(EXTRA_VOICE_NAME) ?: ""
        val inputText = intent.getStringExtra(EXTRA_INPUT_TEXT) ?: ""
        binding.textViewVoiceName.text = voiceName
        binding.textViewInputText.text = inputText

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
        binding.buttonPlayPause.setOnClickListener {
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
                is PlayerState.Idle -> {
                    binding.progressBarLoading.visibility = View.VISIBLE
                    binding.layoutControls.visibility = View.GONE
                    binding.textViewError.visibility = View.GONE
                }
                is PlayerState.Loading -> {
                    binding.progressBarLoading.visibility = View.VISIBLE
                    binding.layoutControls.visibility = View.GONE
                    binding.textViewError.visibility = View.GONE
                }
                is PlayerState.Ready -> {
                    binding.progressBarLoading.visibility = View.GONE
                    binding.layoutControls.visibility = View.VISIBLE
                    binding.textViewError.visibility = View.GONE
                    binding.buttonPlayPause.text = "Play"
                    viewModel.play()
                }
                is PlayerState.Playing -> {
                    binding.progressBarLoading.visibility = View.GONE
                    binding.layoutControls.visibility = View.VISIBLE
                    binding.textViewError.visibility = View.GONE
                    binding.buttonPlayPause.text = "Pause"
                    startProgressUpdates()
                }
                is PlayerState.Paused -> {
                    binding.buttonPlayPause.text = "Play"
                    stopProgressUpdates()
                }
                is PlayerState.Completed -> {
                    binding.buttonPlayPause.text = "Play"
                    stopProgressUpdates()
                }
                is PlayerState.Error -> {
                    binding.progressBarLoading.visibility = View.GONE
                    binding.layoutControls.visibility = View.GONE
                    binding.textViewError.visibility = View.VISIBLE
                    binding.textViewError.text = state.message
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
        
        // Generate unique filename
        val timestamp = System.currentTimeMillis()
        val filename = "AUD-${timestamp}"
        // Update filename display in dialog (the hardcoded text in layout will be replaced dynamically)
        
        // Set up tick animation (you can add Lottie animation here)
        // sheetBinding.imgTick.setImageResource(com.voicechanger.app.R.drawable.ic_tick_success)
        
        sheetBinding.btnDone.setOnClickListener {
            // Save file logic here
            saveAudioFile(filename)
            dialog.dismiss()
        }
        
        sheetBinding.btnShare.setOnClickListener {
            // Share file logic here
            shareAudioFile(filename)
        }
        
        dialog.show()
    }
    
    private fun saveAudioFile(filename: String) {
        // Implement file saving logic
        Toast.makeText(this, "Audio saved as $filename", Toast.LENGTH_SHORT).show()
    }
    
    private fun shareAudioFile(filename: String) {
        // Implement file sharing logic
        Toast.makeText(this, "Sharing $filename", Toast.LENGTH_SHORT).show()
    }
}
