package com.example.clipcraft.di

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.example.clipcraft.domain.repository.AuthRepository
import com.example.clipcraft.domain.repository.EditHistoryRepository
import com.example.clipcraft.domain.repository.GalleryRepository
import com.example.clipcraft.domain.repository.VideoProcessingRepository
import com.example.clipcraft.services.AuthService
import com.example.clipcraft.services.GalleryLoaderService
import com.example.clipcraft.services.VideoAnalyzerService
import com.example.clipcraft.services.VideoEditorService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-модуль для предоставления репозиториев.
 * Этот модуль отвечает за создание и внедрение репозиториев в приложении.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(authService: AuthService): AuthRepository {
        return AuthRepository(authService)
    }

    @Provides
    @Singleton
    fun provideGalleryRepository(
        galleryLoader: GalleryLoaderService,
        videoAnalyzer: VideoAnalyzerService
    ): GalleryRepository {
        return GalleryRepository(galleryLoader, videoAnalyzer)
    }

    @Provides
    @Singleton
    fun provideEditHistoryRepository(
        sharedPreferences: SharedPreferences,
        gson: Gson
    ): EditHistoryRepository {
        return EditHistoryRepository(sharedPreferences, gson)
    }

    @Provides
    @Singleton
    fun provideVideoProcessingRepository(
        @ApplicationContext context: Context,
        gson: Gson,
        sharedPreferences: SharedPreferences,
        videoEditorService: VideoEditorService
    ): VideoProcessingRepository {
        // WorkManager получается из контекста
        val workManager = WorkManager.getInstance(context)
        return VideoProcessingRepository(workManager, gson, sharedPreferences, videoEditorService)
    }

    @Provides
    @Singleton
    fun provideVideoEditorService(
        @ApplicationContext context: Context
    ): VideoEditorService {
        return VideoEditorService(context)
    }
}
