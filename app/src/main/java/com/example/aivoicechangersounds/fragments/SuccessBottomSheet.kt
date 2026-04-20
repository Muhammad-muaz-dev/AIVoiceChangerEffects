package com.example.aivoicechangersounds.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.voicechanger.app.R
import com.voicechanger.app.databinding.DialogueSaveFileBinding

class SuccessBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogueSaveFileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogueSaveFileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadTickAnimation()

        binding.btnDone.setOnClickListener {
            dismiss()
        }
    }

    private fun loadTickAnimation() {
        Glide.with(this)
            .asGif()
            .load(R.drawable.success_tick)
            .into(binding.imgTick)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}