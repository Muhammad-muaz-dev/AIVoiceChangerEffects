package com.example.aivoicechangersounds.data.api

/**
 * Annotate any Retrofit interface with this to override the default base URL.
 * If not present, ServiceFactory falls back to the default URL.
 *
 * Example:
 *   @ApiUrl(ApiUrls.TTS_BASE_URL)
 *   interface ApiServiceTTS { ... }
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiUrl(val url: String)
