package com.example.aivoicechangersounds.activities


import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.aivoicechangersounds.data.models.GenerateVoiceResponse
import com.example.aivoicechangersounds.Viewmodels.VoiceEffectViewModel
import com.example.aivoicechangersounds.ui.voiceai.VoiceGridAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityVoiceEffectBinding
import com.voicechanger.app.databinding.DialogueSavingAudioFileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class VoiceEffectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceEffectBinding
    private val viewModel: VoiceEffectViewModel by viewModels()

    private lateinit var voiceAdapter: VoiceGridAdapter
    private var savingDialog: BottomSheetDialog? = null

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            viewModel.updateProgress()
            progressHandler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVoiceEffectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val audioFilePath = intent.getStringExtra(RecordingActivity.EXTRA_AUDIO_FILE_PATH)
        if (audioFilePath.isNullOrBlank()) {
            Toast.makeText(this, "No audio file received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel.setAudioFilePath(audioFilePath)

        setupToolbar()
        setupAudioControls()
        setupVoiceGrid()
        observeViewModel()

        // Auto-fetch voices on entry
        viewModel.fetchVoices()
    }

    private fun setupToolbar() {
        binding.backarrow.setOnClickListener {
            finish()
        }

        // ic_tick2 button — send recorded audio + selected voice to backend
        binding.toolbarAIVoices.findViewById<View>(R.id.btnTick)?.setOnClickListener {
            onTickClicked()
        }
    }

    /**
     * Called when the tick/confirm button in toolbar is pressed.
     * Validates that a voice is selected, then sends to backend.
     */
    private fun onTickClicked() {
        if (viewModel.selectedVoice.value == null) {
            Toast.makeText(this, "Please select a voice first", Toast.LENGTH_SHORT).show()
            return
        }
        showSavingBottomSheet()
        viewModel.generateVoiceEffect()
    }

    private fun setupAudioControls() {
        // Play/Replay button
        binding.buttonReplay.setOnClickListener {
            viewModel.playPauseAudio()
        }

        // SeekBar
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

        // Volume button — toggle volume up/down on each click
        // You can replace this with a volume slider dialog if preferred
        binding.root.findViewById<View>(R.id.btnVolume)?.setOnClickListener {
            val currentVolume = viewModel.volume.value
            if (currentVolume > 0.5f) {
                viewModel.setVolume(0.2f)
                Toast.makeText(this, "Volume: Low", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.setVolume(1.0f)
                Toast.makeText(this, "Volume: High", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupVoiceGrid() {
        voiceAdapter = VoiceGridAdapter { voice ->
            viewModel.selectVoice(voice)
        }

        binding.rvVoices.apply {
            layoutManager = GridLayoutManager(this@VoiceEffectActivity, 3)
            adapter = voiceAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe voices list
                launch {
                    viewModel.voices.collect { voices ->
                        voiceAdapter.submitList(voices)
                    }
                }

                // Observe selected voice
                launch {
                    viewModel.selectedVoice.collect { voice ->
                        voiceAdapter.setSelectedVoice(voice)
                    }
                }

                // Observe playback state
                launch {
                    viewModel.isPlaying.collect { playing ->
                        if (playing) {
                            binding.buttonReplay.setImageResource(R.drawable.ic_pause)
                            startProgressUpdates()
                        } else {
                            binding.buttonReplay.setImageResource(R.drawable.ic_playbutton)
                            stopProgressUpdates()
                        }
                    }
                }

                // Observe total duration
                launch {
                    viewModel.totalDuration.collect { duration ->
                        binding.seekBar.max = duration
                        binding.textViewTotalTime.text = formatTime(duration)
                    }
                }

                // Observe current position
                launch {
                    viewModel.currentPosition.collect { position ->
                        binding.seekBar.progress = position
                        binding.textViewCurrentTime.text = formatTime(position)
                    }
                }

                // Observe voice generation result
                launch {
                    viewModel.generateResult.collect { result ->
                        if (result != null) {
                            dismissSavingBottomSheet()
                            navigateToAudioPlayer(result)
                            viewModel.clearGenerateResult()
                        }
                    }
                }

                // Observe voice generation error
                launch {
                    viewModel.generateError.collect { error ->
                        if (error != null) {
                            dismissSavingBottomSheet()
                            Toast.makeText(this@VoiceEffectActivity, error, Toast.LENGTH_LONG).show()
                            viewModel.clearGenerateResult()
                        }
                    }
                }

                // Observe loading state for voices
                launch {
                    viewModel.voicesError.collect { error ->
                        if (error != null) {
                            Toast.makeText(this@VoiceEffectActivity, error, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showSavingBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = DialogueSavingAudioFileBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        dialog.setCancelable(false)
        dialog.show()
        savingDialog = dialog
    }

    private fun dismissSavingBottomSheet() {
        savingDialog?.dismiss()
        savingDialog = null
    }

    private fun navigateToAudioPlayer(response: GenerateVoiceResponse) {
        val intent = Intent(this, AudioPlayerActivity::class.java).apply {
            putExtra(AudioPlayerActivity.EXTRA_AUDIO_URL, response.audioUrl)
            putExtra(AudioPlayerActivity.EXTRA_AUDIO_BASE64, response.audioBase64)
            putExtra(AudioPlayerActivity.EXTRA_VOICE_NAME, response.voiceName ?: viewModel.selectedVoice.value?.name ?: "")
            putExtra(AudioPlayerActivity.EXTRA_INPUT_TEXT, "Voice Effect Applied")
        }
        startActivity(intent)
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
        if (viewModel.isPlaying.value) {
            startProgressUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdates()
        dismissSavingBottomSheet()
    }
}