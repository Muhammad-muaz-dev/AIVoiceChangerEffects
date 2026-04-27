package com.example.aivoicechangersounds.adapters

import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ItemFileBinding
import com.example.aivoicechangersounds.data.models.FileItem
import java.util.concurrent.TimeUnit

class FileAdapter(
    private val onPlayClicked: (FileItem) -> Unit,
    private val onMenuClicked: (FileItem, android.view.View) -> Unit
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    private var currentlyPlayingPath: String? = null

    fun setPlayingFile(path: String?) {
        val oldPath = currentlyPlayingPath
        currentlyPlayingPath = path
        currentList.forEachIndexed { index, item ->
            if (item.path == oldPath || item.path == path) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FileItem) {
            binding.txtfile.text = item.name

            val duration = formatDuration(item.durationMs)
            val size = formatSize(item.sizeBytes)
            binding.txtTime.text = "$duration | $size"

            val isPlaying = item.path == currentlyPlayingPath
            binding.btnplay.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_playbutton
            )

            binding.btnplay.setOnClickListener {
                onPlayClicked(item)
            }

            binding.icmenu.setOnClickListener { view ->
                onMenuClicked(item, view)
            }
        }

        private fun formatDuration(ms: Long): String {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

        private fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
                else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            }
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem) =
            oldItem.path == newItem.path

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem) =
            oldItem == newItem
    }
}
