package com.example.clipcraft.services

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.clipcraft.models.SelectedVideo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryLoaderService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoAnalyzer: VideoAnalyzerService
) {
    fun loadGalleryVideos(): Flow<List<SelectedVideo>> = flow {
        val videos = withContext(Dispatchers.IO) {
            val videoList = mutableListOf<SelectedVideo>()
            val contentResolver: ContentResolver = context.contentResolver

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
            )

            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (it.moveToNext()) { // Убрали ограничение в 100 видео
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn) ?: "video_$id.mp4"
                    val duration = it.getLong(durationColumn)

                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    // Быстрое создание SelectedVideo без полного анализа
                    val video = SelectedVideo(
                        uri = contentUri,
                        path = contentUri.toString(),
                        fileName = name,
                        durationMs = duration,
                        hasAudio = true // Предполагаем наличие аудио, проверим позже при необходимости
                    )

                    videoList.add(video)
                }
            }

            videoList
        }

        emit(videos)
    }

    // Дополнительный метод для постраничной загрузки (для будущего улучшения)
    fun loadGalleryVideosPaged(offset: Int, limit: Int): Flow<List<SelectedVideo>> = flow {
        val videos = withContext(Dispatchers.IO) {
            val videoList = mutableListOf<SelectedVideo>()
            val contentResolver: ContentResolver = context.contentResolver

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
            )

            val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC LIMIT $limit OFFSET $offset"

            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val duration = it.getLong(durationColumn)

                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    val video = SelectedVideo(
                        uri = contentUri,
                        path = contentUri.toString(),
                        fileName = name,
                        durationMs = duration,
                        hasAudio = true
                    )

                    videoList.add(video)
                }
            }

            videoList
        }

        emit(videos)
    }
}