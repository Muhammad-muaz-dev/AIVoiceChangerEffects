package com.example.aivoicechangersounds.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Repositories are provided via @Inject constructor and @Singleton on the class itself.
}
