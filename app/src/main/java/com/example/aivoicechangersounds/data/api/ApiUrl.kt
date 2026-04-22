package com.example.aivoicechangersounds.data.api

/**
 * Annotate any Retrofit interface with this to override the default base URL.
 * If not present, ServiceFactory falls back to the default URL.
 *
 * Example:
 *   @ApiUrl(ApiUrls.TTS_BASE_URL)
 *   interface ApiServiceTTS { ... }
 */
/**
 * Apply this annotation to any Retrofit interface to declare
 * which base URL it should use.
 *
 * ServiceFactory reads this at runtime and builds the correct
 * Retrofit instance automatically.
 *
 * Example:
 *   @ApiUrl(ApiUrls.TTS_BASE_URL)
 *   interface ApiServiceTTS { ... }
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiUrl(val url: String)