package com.example.clipcraft.di

import android.content.Context
import com.example.clipcraft.utils.LocaleManager
import com.example.clipcraft.utils.VideoEditorStateManager
import com.example.clipcraft.utils.TemporaryFileManager
import com.example.clipcraft.utils.VideoEditorUpdateManager
import com.example.clipcraft.domain.model.VideoStateTransitionManager
import com.example.clipcraft.domain.model.VideoEditorOrchestrator
import com.example.clipcraft.domain.model.VideoStateManager
import com.example.clipcraft.services.VideoRenderingService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {
    
    @Provides
    @Singleton
    fun provideLocaleManager(
        @ApplicationContext context: Context
    ): LocaleManager {
        return LocaleManager(context)
    }
    
    @Provides
    @Singleton
    fun provideVideoEditorStateManager(): VideoEditorStateManager {
        return VideoEditorStateManager()
    }
    
    @Provides
    @Singleton
    fun provideTemporaryFileManager(
        @ApplicationContext context: Context
    ): TemporaryFileManager {
        return TemporaryFileManager(context)
    }
    
    @Provides
    @Singleton
    fun provideVideoEditorUpdateManager(): VideoEditorUpdateManager {
        return VideoEditorUpdateManager()
    }
    
    @Provides
    @Singleton
    fun provideVideoStateTransitionManager(): VideoStateTransitionManager {
        return VideoStateTransitionManager()
    }
    
    @Provides
    @Singleton
    fun provideVideoEditorOrchestrator(
        transitionManager: VideoStateTransitionManager,
        temporaryFileManager: TemporaryFileManager
    ): VideoEditorOrchestrator {
        return VideoEditorOrchestrator(transitionManager, temporaryFileManager)
    }
    
    @Provides
    @Singleton
    fun provideVideoStateManager(
        @ApplicationContext context: Context,
        gson: Gson
    ): VideoStateManager {
        return VideoStateManager(context, gson)
    }
    
    @Provides
    @Singleton
    fun provideVideoRenderingService(
        @ApplicationContext context: Context,
        temporaryFileManager: TemporaryFileManager
    ): VideoRenderingService {
        return VideoRenderingService(context, temporaryFileManager)
    }
}