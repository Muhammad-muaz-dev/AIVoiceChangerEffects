package com.example.aivoicechangersounds.di

import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceVoices
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceTTS
import com.example.aivoicechangersounds.data.api.ApiUrls
import com.example.aivoicechangersounds.data.api.ServiceFactory
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceLanguages
import com.example.aivoicechangersounds.data.api.interfaces.ApiServicesGenerateAudio
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires up every Retrofit service via Hilt.
 *
 * ADDING A NEW SERVICE — only 3 steps:
 *  1. Create your interface in data/api/ and add @ApiUrl(ApiUrls.YOUR_URL)
 *  2. Add the URL constant in ApiUrls.kt (if it is a new server)
 *  3. Add ONE @Provides function below
 *
 * Token handling, logging, and timeouts are all handled by ServiceFactory.
 * You never touch OkHttpClient or Retrofit directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** One shared ServiceFactory — one OkHttpClient for the whole app */
    @Provides
    @Singleton
    fun provideServiceFactory(): ServiceFactory =
        ServiceFactory(defaultApiUrl = ApiUrls.MAIN_BASE_URL)

    // ── Services ──────────────────────────────────────────────────────────────

    /** Speech-to-speech + voices + languages (MAIN_BASE_URL) */
    @Provides
    @Singleton
    fun provideApiService(factory: ServiceFactory): ApiServiceVoices =
        factory.createInstance(ApiServiceVoices::class.java)

    /** Text-to-speech + voices + languages (TTS_BASE_URL) */
    @Provides
    @Singleton
    fun provideApiServiceTTS(factory: ServiceFactory): ApiServiceTTS =
        factory.createInstance(ApiServiceTTS::class.java)
    @Provides
    @Singleton
    fun provideApiServeGenAudio(factory: ServiceFactory): ApiServicesGenerateAudio =
        factory.createInstance(ApiServicesGenerateAudio::class.java)
    @Provides
    @Singleton
    fun provideApiServeLang(factory: ServiceFactory): ApiServiceLanguages =
        factory.createInstance(ApiServiceLanguages::class.java)

    // To add a third service later:
    // @Provides @Singleton
    // fun provideAnalyticsService(factory: ServiceFactory): AnalyticsService =
    //     factory.createInstance(AnalyticsService::class.java)
}