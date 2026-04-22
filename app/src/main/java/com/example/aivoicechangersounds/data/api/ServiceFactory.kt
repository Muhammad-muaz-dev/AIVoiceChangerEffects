package com.example.aivoicechangersounds.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Creates Retrofit service instances.
 *
 * WHAT IT DOES:
 * 1. Reads @ApiUrl on the interface → picks the correct base URL
 * 2. Injects Authorization header on EVERY request via OkHttp interceptor
 *    (no need to add @Header to any method ever again)
 * 3. Shares one OkHttpClient across all services (efficient)
 *
 * ADDING A NEW SERVICE:
 * 1. Create interface in data/api/, annotate with @ApiUrl(ApiUrls.YOUR_URL)
 * 2. Add the URL in ApiUrls.kt if it is a new server
 * 3. Add one @Provides line in NetworkModule.kt
 * That is it — token, logging, timeouts all come for free.
 */
class ServiceFactory(
    private val defaultApiUrl: String = ApiUrls.MAIN_BASE_URL
) {

    // Single shared OkHttpClient — all services share this to avoid
    // creating multiple thread pools and socket connections.
    private val okHttpClient: OkHttpClient by lazy {

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()

            // Auth + common headers interceptor.
            // Reads the token from TokenProvider at REQUEST TIME, not at build time.
            // This means a token refresh is reflected immediately on the next call.
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "AIVoiceChanger/1.0")

                // Only attach Authorization if a token exists
                if (TokenProvider.hasToken()) {
                    requestBuilder.header("Authorization", TokenProvider.getToken())
                }

                chain.proceed(requestBuilder.build())
            }

            // Logging goes AFTER auth so the full request (including token) is visible
            .addInterceptor(logging)

            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Creates a Retrofit service. Base URL resolved in this order:
     *   1. @ApiUrl annotation on the interface
     *   2. defaultApiUrl passed to the ServiceFactory constructor
     */
    fun <T> createInstance(clazz: Class<T>): T {
        val annotation = clazz.annotations.find { it is ApiUrl } as ApiUrl?
        val url = annotation?.url ?: defaultApiUrl
        return retrofit(url).create(clazz)
    }

    private fun retrofit(apiUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(apiUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}