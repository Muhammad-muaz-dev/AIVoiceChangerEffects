package com.voicechanger.app.ui.preview

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.aivoicechangersounds.Viewmodels.PreviewViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityPreviewBinding
import com.voicechanger.app.databinding.DialogueSaveFileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class ActivityPreview : AppCompatActivity() {

    companion object {
        const val EXTRA_AUDIO_FILE_PATH = "extra_audio_file_path"
    }

    private lateinit var binding: ActivityPreviewBinding
    private val viewModel: PreviewViewModel by viewModels()
    private var savingDialog: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val audioFilePath = intent.getStringExtra(EXTRA_AUDIO_FILE_PATH)
        if (audioFilePath.isNullOrBlank()) {
            Toast.makeText(this, "No audio file received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel.initAudio(audioFilePath)

        setupToolbar()
        setupControls()
        setupKebabMenu()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbarpreview.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupControls() {
        // SeekBar listener
        binding.seekBarpre.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Play Original button
        binding.btnPlayOriginal.setOnClickListener {
            viewModel.onPlayOriginalClicked()
        }

        // Play Reverse button
        binding.btnPlayReverse.setOnClickListener {
            viewModel.onPlayReverseClicked()
        }

        // Save to My Files button → show bottom sheet, then save
        binding.btnsave.setOnClickListener {
            showSavingBottomSheet()
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (!musicDir.exists()) musicDir.mkdirs()
            viewModel.saveToFiles(musicDir)
        }

        // Back Home button
        binding.btnback.setOnClickListener {
            finish()
        }
    }

    /**
     * Sets up kebab (three-dot) menu with Rename, Share, Delete options.
     */
    private fun setupKebabMenu() {
        binding.menupreview.setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            popupMenu.menuInflater.inflate(R.menu.menu_preview_kebab, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_rename -> {
                        showRenameDialog()
                        true
                    }
                    R.id.action_share -> {
                        shareAudioFile()
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteConfirmation()
                        true
                    }
                    else -> false
                }
            }

            popupMenu.show()
        }
    }

    /**
     * Shows a dialog with EditText for the user to enter a new file name.
     */
    private fun showRenameDialog() {
        val currentName = viewModel.fileName.value
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(currentName)
            setSelection(currentName.length)
            setPadding(60, 40, 60, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename File")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.renameAudioFile(newName)
                } else {
                    Toast.makeText(this, "File name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Shares the audio file using Android's share intent.
     */
    private fun shareAudioFile() {
        val filePath = viewModel.getAudioFilePath()
        if (filePath == null) {
            Toast.makeText(this, "No audio file to share", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share Audio"))
    }

    /**
     * Shows a confirmation dialog before deleting the audio file.
     */
    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete this audio file? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteAudioFile()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSavingBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = DialogueSaveFileBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        dialog.setCancelable(false)
        dialog.show()
        savingDialog = dialog
    }

    private fun dismissSavingBottomSheet() {
        savingDialog?.dismiss()
        savingDialog = null
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe file name
                launch {
                    viewModel.fileName.collect { name ->
                        binding.filename.text = name
                    }
                }

                // Observe file date
                launch {
                    viewModel.fileDate.collect { date ->
                        binding.recdate.text = date
                    }
                }

                // Observe total duration → seekbar max + right text
                launch {
                    viewModel.totalDuration.collect { duration ->
                        binding.seekBarpre.max = duration
                    }
                }

                // Observe formatted total time → right side of seekbar
                launch {
                    viewModel.formattedTotalTime.collect { time ->
                        binding.totalduration.text = time
                    }
                }

                // Observe current position → seekbar progress
                launch {
                    viewModel.currentPosition.collect { position ->
                        binding.seekBarpre.progress = position
                    }
                }

                // Observe formatted current time → left side of seekbar
                launch {
                    viewModel.formattedCurrentTime.collect { time ->
                        binding.continuetime.text = time
                    }
                }

                // Observe Play Original state → toggle button icon
                launch {
                    viewModel.isPlayingOriginal.collect { playing ->
                        if (playing) {
                            binding.btnPlayOriginal.setCompoundDrawablesWithIntrinsicBounds(
                                R.drawable.icplaying, 0, 0, 0
                            )
                            binding.btnPlayOriginal.text = "Playing..."
                        } else {
                            binding.btnPlayOriginal.setCompoundDrawablesWithIntrinsicBounds(
                                R.drawable.icpausing, 0, 0, 0
                            )
                            binding.btnPlayOriginal.text = "Play Original"
                        }
                    }
                }

                // Observe Play Reverse state → toggle button icon
                launch {
                    viewModel.isPlayingReverse.collect { playing ->
                        if (playing) {
                            binding.btnPlayReverse.setCompoundDrawablesWithIntrinsicBounds(
                                R.drawable.icpausing, 0, 0, 0
                            )
                            binding.btnPlayReverse.text = "Playing..."
                        } else {
                            binding.btnPlayReverse.setCompoundDrawablesWithIntrinsicBounds(
                                R.drawable.icplaying, 0, 0, 0
                            )
                            binding.btnPlayReverse.text = "Play Reverse"
                        }
                    }
                }

                // Observe save success → dismiss bottom sheet + show toast
                launch {
                    viewModel.saveSuccess.collect { success ->
                        if (success) {
                            dismissSavingBottomSheet()
                            Toast.makeText(
                                this@ActivityPreview,
                                "Audio saved to Music folder",
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.clearSaveSuccess()
                        }
                    }
                }

                // Observe delete success → finish activity
                launch {
                    viewModel.deleteSuccess.collect { success ->
                        if (success) {
                            Toast.makeText(
                                this@ActivityPreview,
                                "Audio file deleted",
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.clearDeleteSuccess()
                            finish()
                        }
                    }
                }

                // Observe rename success
                launch {
                    viewModel.renameSuccess.collect { success ->
                        if (success) {
                            Toast.makeText(
                                this@ActivityPreview,
                                "File renamed successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.clearRenameSuccess()
                        }
                    }
                }

                // Observe errors → dismiss bottom sheet if showing + show toast
                launch {
                    viewModel.errorMessage.collect { error ->
                        if (error != null) {
                            dismissSavingBottomSheet()
                            Toast.makeText(this@ActivityPreview, error, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissSavingBottomSheet()
    }
}
