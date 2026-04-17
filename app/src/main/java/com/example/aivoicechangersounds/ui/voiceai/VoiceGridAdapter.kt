package com.example.aivoicechangersounds.ui.voiceai


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.aivoicechangersounds.data.models.Voice
import com.voicechanger.app.R
import com.voicechanger.app.databinding.ItemVoiceBinding


class VoiceGridAdapter(
    private val onVoiceSelected: (Voice) -> Unit
) : ListAdapter<Voice, VoiceGridAdapter.VoiceViewHolder>(VoiceDiffCallback()) {

    private var selectedVoiceId: String? = null

    fun setSelectedVoice(voice: Voice?) {
        val oldId = selectedVoiceId
        selectedVoiceId = voice?.id
        // Refresh changed items only for performance
        currentList.forEachIndexed { index, v ->
            if (v.id == oldId || v.id == selectedVoiceId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceViewHolder {
        val binding = ItemVoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VoiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VoiceViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).id == selectedVoiceId)
    }

    inner class VoiceViewHolder(private val binding: ItemVoiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(voice: Voice, isSelected: Boolean) {
            // Only avatar and name are visible — voice ID and other fields are invisible
            binding.textViewVoiceName.text = voice.name

            // Load avatar image
            Glide.with(binding.root.context)
                .load(voice.avatarUrl)
                .placeholder(R.drawable.ic_voice_placeholder)
                .error(R.drawable.ic_voice_placeholder)
                .circleCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.imageViewAvatar)

            // Visual selection state
            binding.root.isSelected = isSelected
            binding.viewSelectionOverlay.visibility =
                if (isSelected) android.view.View.VISIBLE else android.view.View.GONE

            binding.root.setOnClickListener {
                onVoiceSelected(voice)
            }
        }
    }

    class VoiceDiffCallback : DiffUtil.ItemCallback<Voice>() {
        override fun areItemsTheSame(oldItem: Voice, newItem: Voice) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Voice, newItem: Voice) = oldItem == newItem
    }
}
