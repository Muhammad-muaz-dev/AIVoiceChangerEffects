package com.example.aivoicechangersounds.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.aivoicechangersounds.data.models.TranslationHistoryItem
import com.voicechanger.app.databinding.ItemVoiceTranslateBinding
import java.util.Locale

class TranslationHistoryAdapter(
    private val items: MutableList<TranslationHistoryItem>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<TranslationHistoryAdapter.ViewHolder>() {

    private var tts: TextToSpeech? = null

    inner class ViewHolder(val binding: ItemVoiceTranslateBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVoiceTranslateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        with(holder.binding) {
            txtlang.text = item.sourceLanguage
            txtspeak.text = item.sourceText
            txtlang2.text = item.targetLanguage
            txtoutput.text = item.translatedText

            actionslayout.visibility = View.GONE

            triup.setOnClickListener {
                actionslayout.visibility =
                    if (actionslayout.visibility == View.GONE) View.VISIBLE else View.GONE
            }

            copy.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("translated_text", item.translatedText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }

            speak.setOnClickListener {
                speakText(context, item.translatedText, item.targetLanguage)
            }

            share.setOnClickListener {
                val shareText = "${item.sourceLanguage}: ${item.sourceText}\n" +
                        "${item.targetLanguage}: ${item.translatedText}"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share translation"))
            }

            delete.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDeleteClick(pos)
                }
            }
        }
    }

    private fun speakText(context: Context, text: String, languageName: String) {
        tts?.shutdown()
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale.getAvailableLocales().firstOrNull {
                    it.displayLanguage.equals(languageName, ignoreCase = true) ||
                            it.displayName.equals(languageName, ignoreCase = true)
                } ?: Locale.US

                tts?.language = locale
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translate_speak")
            }
        }
    }

    fun removeItem(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size)
        }
    }

    fun addItem(item: TranslationHistoryItem) {
        items.add(0, item)
        notifyItemInserted(0)
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
