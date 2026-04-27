package com.example.aivoicechangersounds.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aivoicechangersounds.Viewmodels.FileViewModel
import com.example.aivoicechangersounds.adapters.FileAdapter
import com.example.aivoicechangersounds.data.models.FileCategory
import com.example.aivoicechangersounds.data.models.FileItem
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ActivityFileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class ActivityFile : AppCompatActivity() {

    private lateinit var binding: ActivityFileBinding
    private val viewModel: FileViewModel by viewModels()
    private lateinit var fileAdapter: FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        setupFilterButtons()
        observeViewModel()

        viewModel.loadFiles()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadFiles()
    }

    private fun setupToolbar() {
        binding.ArrowBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            onPlayClicked = { fileItem -> viewModel.playOrPause(fileItem) },
            onMenuClicked = { fileItem, view -> showFileMenu(fileItem, view) }
        )

        binding.recyclerviewFiles.apply {
            layoutManager = LinearLayoutManager(this@ActivityFile)
            adapter = fileAdapter
        }
    }

    private fun setupFilterButtons() {
        val buttons = mapOf(
            binding.allfiles to FileCategory.ALL,
            binding.aivoice to FileCategory.AI_VOICE,
            binding.reverseVoic to FileCategory.REVERSE,
            binding.voiceEffect to FileCategory.EFFECT,
            binding.translate to FileCategory.TRANSLATE
        )

        buttons.forEach { (button, category) ->
            button.setOnClickListener {
                viewModel.selectCategory(category)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredFiles.collect { files ->
                        fileAdapter.submitList(files)
                    }
                }

                launch {
                    viewModel.playingFilePath.collect { path ->
                        fileAdapter.setPlayingFile(path)
                    }
                }

                launch {
                    viewModel.selectedCategory.collect { category ->
                        updateFilterButtonStates(category)
                    }
                }
            }
        }
    }

    private fun updateFilterButtonStates(selected: FileCategory) {
        val buttons = mapOf(
            binding.allfiles to FileCategory.ALL,
            binding.aivoice to FileCategory.AI_VOICE,
            binding.reverseVoic to FileCategory.REVERSE,
            binding.voiceEffect to FileCategory.EFFECT,
            binding.translate to FileCategory.TRANSLATE
        )

        buttons.forEach { (button, category) ->
            if (category == selected) {
                button.setBackgroundColor(getColor(R.color.accent))
                button.setTextColor(getColor(R.color.white))
            } else {
                button.setBackgroundColor(android.graphics.Color.parseColor("#F4F2FF"))
                button.setTextColor(getColor(R.color.black))
            }
        }
    }

    private fun showFileMenu(fileItem: FileItem, anchor: android.view.View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_preview_kebab, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_rename -> {
                    showRenameDialog(fileItem)
                    true
                }
                R.id.action_share -> {
                    shareFile(fileItem)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation(fileItem)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameDialog(fileItem: FileItem) {
        val currentName = File(fileItem.path).nameWithoutExtension
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(currentName)
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.renameFile(fileItem, newName)
                    Toast.makeText(this, "File renamed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareFile(fileItem: FileItem) {
        val file = File(fileItem.path)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${applicationInfo.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share audio via"))
    }

    private fun showDeleteConfirmation(fileItem: FileItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete \"${fileItem.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteFile(fileItem)
                Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
