package com.example.aivoicechangersounds.data.repository

import android.util.Log
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceLanguages
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceTranslate
import com.example.aivoicechangersounds.data.models.Language
import com.example.aivoicechangersounds.data.models.TranslateRequest
import com.example.aivoicechangersounds.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslateRepository @Inject constructor(
    private val apiTranslate: ApiServiceTranslate,
    private val apiLanguages: ApiServiceLanguages
) {

    suspend fun getLanguages(): Resource<List<Language>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiLanguages.getLanguages()
                if (response.isSuccessful && response.body() != null) {
                    val languages = response.body()!!.data.languages
                    Log.d("TranslateRepo", "Languages size: ${languages.size}")
                    Resource.Success(languages)
                } else {
                    Resource.Error("Error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    suspend fun translate(text: String, targetLang: String): Resource<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = TranslateRequest(text = text, targetLang = targetLang)
                val response = apiTranslate.translate(request)
                if (response.isSuccessful && response.body() != null) {
                    val translatedText = response.body()!!.data.translatedText
                    Log.d("TranslateRepo", "Translated: $translatedText")
                    Resource.Success(translatedText)
                } else {
                    Resource.Error("Error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }
}
