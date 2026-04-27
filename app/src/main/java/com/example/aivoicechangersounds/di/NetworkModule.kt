package com.example.aivoicechangersounds.di

import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceVoices
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceTTS
import com.example.aivoicechangersounds.data.api.ApiUrls
import com.example.aivoicechangersounds.data.api.ServiceFactory
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceLanguages
import com.example.aivoicechangersounds.data.api.interfaces.ApiServiceTranslate
import com.example.aivoicechangersounds.data.api.interfaces.ApiServicesGenerateAudio
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideServiceFactory(): ServiceFactory =
        ServiceFactory(defaultApiUrl = ApiUrls.MAIN_BASE_URL)
    @Provides
    @Singleton
    fun provideApiService(factory: ServiceFactory): ApiServiceVoices =
        factory.createInstance(ApiServiceVoices::class.java)

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

    /** Translation service (MAIN_BASE_URL) */
    @Provides
    @Singleton
    fun provideApiServiceTranslate(factory: ServiceFactory): ApiServiceTranslate =
        factory.createInstance(ApiServiceTranslate::class.java)
}