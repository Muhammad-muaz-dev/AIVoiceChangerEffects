package com.example.aivoicechangersounds.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.example.aivoicechangersounds.data.models.GenerateAudioResponse
import com.example.aivoicechangersounds.Viewmodels.VoiceEffectViewModel
import com.example.aivoicechangersounds.ui.voiceai.VoiceGridAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityVoiceEffectBinding
import com.voicechanger.app.databinding.DialogueSavingAudioFileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.io.File

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

        // ── Step 1: Read data from Intent ─────────────────────────────────────
//        val audioFilePath = intent.getStringExtra(RecordingActivity.EXTRA_AUDIO_FILE_PATH) ?: ""
//        val transcribedText = intent.getStringExtra(RecordingActivity.EXTRA_TRANSCRIBED_TEXT) ?: ""

        val audioFilePath = RecordingActivity.filepath
        val transcribedText = RecordingActivity.text
        Log.d("VoiceEffectActivity", "audioFilePath='$audioFilePath'")
        Log.d("VoiceEffectActivity", "transcribedText='$transcribedText'")

        if (audioFilePath.isBlank() && transcribedText.isBlank()) {
            Toast.makeText(this, "No audio or text received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ── Step 2: Pass audio file to ViewModel for local playback ───────────
        if (audioFilePath.isNotBlank() && File(audioFilePath).exists()) {
            viewModel.setAudioFilePath(audioFilePath)
        }
        binding.tvHiddenTranscribedText.text = transcribedText.trim()
        Log.d("voiceeffect text","the text is ${transcribedText}")
        viewModel.setTranscribedText(transcribedText)

        setupToolbar()
        setupAudioControls()
        setupVoiceGrid()
        observeViewModel()

        // Fetch voices as soon as screen opens
        viewModel.fetchVoices()
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.backarrow.setOnClickListener { finish() }

        // Tick button → send to backend
        binding.toolbarAIVoices.findViewById<View>(R.id.btnTick)?.setOnClickListener {
            onTickClicked()
        }
    }


    private fun onTickClicked() {
        if (viewModel.selectedVoice.value == null) {
            Toast.makeText(this, "Please select a voice first", Toast.LENGTH_SHORT).show()
            return
        }

        val textToSend = binding.tvHiddenTranscribedText.text?.toString()?.trim().orEmpty()
        Log.d("VoiceEffectActivity", "Tick clicked — visible transcribed text='$textToSend'")
        if (textToSend.isBlank()) {
            Toast.makeText(this, "No speech detected. Please record again.", Toast.LENGTH_SHORT).show()
            return
        }

        showSavingBottomSheet()
        viewModel.generateVoiceEffect()
    }

    // ── Audio controls (play back the locally recorded file) ──────────────────

    private fun setupAudioControls() {
        binding.buttonReplay.setOnClickListener {
            if (viewModel.totalDuration.value <= 0) {
                Toast.makeText(this, "Recorded audio not available yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.playPauseAudio()
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

    // ── Voice grid ────────────────────────────────────────────────────────────

    private fun setupVoiceGrid() {
        voiceAdapter = VoiceGridAdapter { voice -> viewModel.selectVoice(voice) }
        binding.rvVoices.apply {
            layoutManager = GridLayoutManager(this@VoiceEffectActivity, 3)
            adapter = voiceAdapter
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.voices.collect { voices ->
                        voiceAdapter.submitList(voices)
                    }
                }

                launch {
                    viewModel.selectedVoice.collect { voice ->
                        voiceAdapter.setSelectedVoice(voice)
                    }
                }

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

                launch {
                    viewModel.totalDuration.collect { duration ->
                        binding.seekBar.max = duration
                        binding.textViewTotalTime.text = formatTime(duration)
                    }
                }

                launch {
                    viewModel.currentPosition.collect { position ->
                        binding.seekBar.progress = position
                        binding.textViewCurrentTime.text = formatTime(position)
                    }
                }
                launch {
                    viewModel.generateResult.collect { result ->
                        if (result != null) {
                            dismissSavingBottomSheet()
                            navigateToAudioPlayer(result)
                            viewModel.clearGenerateResult()
                        }
                    }
                }

                // Backend call failed → show error
                launch {
                    viewModel.generateError.collect { error ->
                        if (error != null) {
                            dismissSavingBottomSheet()
                            Toast.makeText(this@VoiceEffectActivity, error, Toast.LENGTH_LONG).show()
                            viewModel.clearGenerateResult()
                        }
                    }
                }

                launch {
                    viewModel.voicesError.collect { error ->
                        if (error != null) {
                            Toast.makeText(this@VoiceEffectActivity, "Voices: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateToAudioPlayer(response: GenerateAudioResponse) {
        val intent = Intent(this, AudioPlayerActivity::class.java).apply {
            putExtra(AudioPlayerActivity.EXTRA_AUDIO_URL, response.audioUrl)
            putExtra(AudioPlayerActivity.EXTRA_AUDIO_BASE64, response.audioBase64)
            putExtra(AudioPlayerActivity.EXTRA_FILE_PATH, response.filePath)
            putExtra(AudioPlayerActivity.EXTRA_VOICE_NAME, viewModel.selectedVoice.value?.name ?: "")
            putExtra(AudioPlayerActivity.EXTRA_INPUT_TEXT, binding.tvHiddenTranscribedText.text?.toString() ?: "")
        }
        startActivity(intent)
    }

    // ── Bottom sheet ──────────────────────────────────────────────────────────

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

    // ── Progress updates ──────────────────────────────────────────────────────

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
        if (viewModel.isPlaying.value) startProgressUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdates()
        dismissSavingBottomSheet()
    }
}