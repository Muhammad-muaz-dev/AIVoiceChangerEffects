package com.example.aivoicechangersounds.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Creates Retrofit service instances.
 *
 * Rules:
 * 1. If the interface is annotated with @ApiUrl, that URL is used.
 * 2. Otherwise the defaultApiUrl passed to the constructor is used.
 *
 * This means every service interface just needs one annotation to
 * point to its own backend – no extra Retrofit/OkHttp setup needed.
 */
class ServiceFactory(
    private val defaultApiUrl: String = ApiUrls.MAIN_BASE_URL
) {

    // ── Single shared OkHttpClient ────────────────────────────────────────────

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Attach common headers to every request automatically
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "AIVoiceChanger/1.0")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Public factory method ─────────────────────────────────────────────────

    /**
     * Create a Retrofit service instance.
     *
     * Usage:
     *   val ttsService = serviceFactory.createInstance(ApiServiceTTS::class.java)
     *
     * The base URL is resolved in this order:
     *   1. @ApiUrl annotation on the interface class
     *   2. defaultApiUrl passed to ServiceFactory constructor
     */
    fun <T> createInstance(clazz: Class<T>): T {
        // Step 1 – look for @ApiUrl on the interface
        val annotation = clazz.annotations.find { it is ApiUrl } as ApiUrl?
        // Step 2 – use annotated URL, or fall back to the default
        val url = annotation?.url ?: defaultApiUrl
        // Step 3 – build Retrofit and create the service
        return retrofit(url).create(clazz)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun retrofit(apiUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(apiUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}