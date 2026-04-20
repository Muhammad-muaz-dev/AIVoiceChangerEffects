package com.example.aivoicechangersounds.ui.voiceai

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aivoicechangersounds.data.models.Language
import com.voicechanger.app.databinding.ItemLanguageBinding

class LanguageAdapter(
    private val list: List<Language>,
    private var selectedPosition: Int,
    private val onClick: (Language) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemLanguageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        with(holder.binding) {
            tvLanguage.text = item.displayName
            radioSelect.isChecked = position == selectedPosition

            root.setOnClickListener {
                selectedPosition = position
                notifyDataSetChanged()
                onClick(item)
            }
        }
    }
}