package com.example.aivoicechangersounds.di

import android.content.Context
import com.example.aivoicechangersounds.data.repository.AudioRecorderRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAudioRecorderRepository(
        @ApplicationContext context: Context
    ): AudioRecorderRepository {
        return AudioRecorderRepository(context)
    }
}