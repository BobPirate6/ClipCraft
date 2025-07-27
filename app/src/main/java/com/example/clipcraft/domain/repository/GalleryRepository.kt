package com.example.clipcraft.domain.repository

import android.net.Uri
import com.example.clipcraft.models.SelectedVideo
import com.example.clipcraft.services.GalleryLoaderService
import com.example.clipcraft.services.VideoAnalyzerService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для работы с медиа-галереей устройства.
 */
@Singleton
class GalleryRepository @Inject constructor(
    private val galleryLoader: GalleryLoaderService,
    private val videoAnalyzer: VideoAnalyzerService
) {

    /**
     * Загружает список всех видео из галереи.
     */
    fun loadGalleryVideos(): Flow<List<SelectedVideo>> {
        return galleryLoader.loadGalleryVideos()
    }

    /**
     * Получает детальную информацию о видео по его Uri.
     */
    suspend fun getVideoInfo(uri: Uri): SelectedVideo? {
        return videoAnalyzer.getVideoInfo(uri)
    }
}
