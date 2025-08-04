package com.example.clipcraft.services

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import com.example.clipcraft.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
@Singleton
class VideoEditorService @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "VideoEditorService"
    }

    suspend fun executeEditPlan(
        editPlan: EditPlan,
        videoMap: Map<String, SelectedVideo>,
        exportSettings: ExportSettings,
        onProgress: (Float) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {

        Log.d(TAG, "=== Начало executeEditPlan ===")
        Log.d(TAG, "План монтажа содержит ${editPlan.finalEdit.size} сегментов")

        // Логируем план монтажа
        editPlan.finalEdit.forEachIndexed { index, segment ->
            Log.d(TAG, "Сегмент $index: видео='${segment.sourceVideo}', start=${segment.startTime}s, end=${segment.endTime}s")
        }

        // Создаем нормализованную карту видео для поиска
        val normalizedVideoMap = mutableMapOf<String, SelectedVideo>()
        videoMap.forEach { (key, video) ->
            // Добавляем оригинальный ключ
            normalizedVideoMap[key] = video
            // Добавляем ключ без расширения
            val nameWithoutExt = key.removeSuffix(".mp4").removeSuffix(".MP4")
            normalizedVideoMap[nameWithoutExt] = video
            // Добавляем ключ с расширением если его нет
            if (!key.endsWith(".mp4", ignoreCase = true)) {
                normalizedVideoMap["$key.mp4"] = video
            }
        }

        Log.d(TAG, "Нормализованная карта видео содержит ${normalizedVideoMap.size} ключей")

        // Проверяем, что план не пустой
        if (editPlan.finalEdit.isEmpty()) {
            Log.e(TAG, "План монтажа пуст!")
            throw IllegalStateException("План монтажа пуст")
        }

        // Создаем временный файл для результата
        val outputFile = File(context.cacheDir, "edited_video_${System.currentTimeMillis()}.mp4")
        Log.d(TAG, "Выходной файл: ${outputFile.absolutePath}")

        try {
            // Создаем медиа-элементы для каждого сегмента
            val editedMediaItems = editPlan.finalEdit.mapIndexedNotNull { index, segment ->
                Log.d(TAG, "Обработка сегмента $index: ищем видео '${segment.sourceVideo}'")

                // Ищем видео в нормализованной карте
                val sourceVideo = normalizedVideoMap[segment.sourceVideo]
                    ?: normalizedVideoMap[segment.sourceVideo.removeSuffix(".mp4")]
                    ?: normalizedVideoMap[segment.sourceVideo.removeSuffix(".MP4")]

                if (sourceVideo == null) {
                    Log.e(TAG, "❌ Видео не найдено для сегмента: '${segment.sourceVideo}'")
                    Log.e(TAG, "Доступные ключи в карте: ${normalizedVideoMap.keys.joinToString()}")
                    throw IllegalStateException("Видео '${segment.sourceVideo}' не найдено в карте видео")
                }

                Log.d(TAG, "✅ Видео найдено: ${sourceVideo.fileName}")

                // Создаем MediaItem с обрезкой
                val startMs = (segment.startTime * 1000).toLong()
                val endMs = (segment.endTime * 1000).toLong()
                Log.d(TAG, "Обрезка: ${startMs}ms - ${endMs}ms (${endMs - startMs}ms)")

                val mediaItem = MediaItem.Builder()
                    .setUri(sourceVideo.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build()
                    )
                    .build()

                // Оборачиваем в EditedMediaItem
                val editedItem = EditedMediaItem.Builder(mediaItem).build()
                Log.d(TAG, "✅ EditedMediaItem создан")
                editedItem
            }

            Log.d(TAG, "Создано ${editedMediaItems.size} из ${editPlan.finalEdit.size} медиа-элементов")

            if (editedMediaItems.isEmpty()) {
                Log.e(TAG, "❌ Не удалось создать ни одного медиа-элемента!")
                throw IllegalStateException("Не удалось создать медиа-элементы")
            }

            // Выполняем монтаж
            Log.d(TAG, "Начинаем обработку видео...")
            val success = processVideo(
                editedMediaItems = editedMediaItems,
                outputPath = outputFile.absolutePath,
                onProgress = onProgress
            )

            if (!success) {
                Log.e(TAG, "❌ Ошибка обработки видео")
                throw Exception("Ошибка обработки видео")
            }

            Log.d(TAG, "✅ Видео обработано, сохраняем как временный файл...")

            // Возвращаем путь к временному файлу вместо сохранения в галерею
            // Файл будет сохранен в галерею только при явном нажатии кнопки "Сохранить"
            Log.d(TAG, "✅ Временное видео готово: ${outputFile.absolutePath}")
            outputFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка в executeEditPlan", e)
            outputFile.delete() // Удаляем временный файл в случае ошибки
            throw e
        }
    }

    private suspend fun processVideo(
        editedMediaItems: List<EditedMediaItem>,
        outputPath: String,
        onProgress: (Float) -> Unit
    ): Boolean = suspendCancellableCoroutine { continuation ->

        Log.d(TAG, "processVideo: обработка ${editedMediaItems.size} элементов")

        // Transformer должен быть создан и запущен в главном потоке
        MainScope().launch {
            try {
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            Log.d(TAG, "✅ Transformer completed successfully")
                            if (!continuation.isCompleted) {
                                continuation.resume(true)
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            Log.e(TAG, "❌ Transformer error", exportException)
                            if (!continuation.isCompleted) {
                                continuation.resumeWithException(exportException)
                            }
                        }
                    })
                    .build()

                // Создаем композицию
                val composition = if (editedMediaItems.size == 1) {
                    Log.d(TAG, "Создаем композицию с одним элементом")
                    Composition.Builder(
                        EditedMediaItemSequence(editedMediaItems.first())
                    )
                    .experimentalSetForceAudioTrack(true)
                    .build()
                } else {
                    Log.d(TAG, "Создаем композицию с ${editedMediaItems.size} элементами")
                    Composition.Builder(
                        EditedMediaItemSequence(editedMediaItems)
                    )
                    .experimentalSetForceAudioTrack(true)
                    .build()
                }

                // Запускаем экспорт
                Log.d(TAG, "Запускаем экспорт в: $outputPath")
                transformer.start(composition, outputPath)

                // Отслеживаем отмену
                continuation.invokeOnCancellation {
                    Log.w(TAG, "Экспорт отменен")
                    MainScope().launch {
                        transformer.cancel()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при создании Transformer", e)
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    private suspend fun saveToGallery(videoFile: File): Uri = withContext(Dispatchers.IO) {
        Log.d(TAG, "Сохранение в галерею: ${videoFile.absolutePath}, размер: ${videoFile.length()} байт")

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ClipCraft_${System.currentTimeMillis()}")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.SIZE, videoFile.length())
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ClipCraft")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = context.contentResolver.insert(collection, contentValues)
            ?: throw IllegalStateException("Failed to create media store entry")

        Log.d(TAG, "Создана запись в MediaStore: $uri")

        // Копируем файл в MediaStore
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            videoFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        // Если Android Q+, сбрасываем статус IS_PENDING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }

        Log.d(TAG, "✅ Видео сохранено в галерею: $uri")
        uri
    }
    
    /**
     * Сохраняет видео из временного файла в галерею
     * @param tempVideoPath путь к временному видео файлу
     * @return Uri сохраненного видео в MediaStore
     */
    suspend fun saveVideoToGallery(tempVideoPath: String): Uri = withContext(Dispatchers.IO) {
        Log.d(TAG, "Сохранение видео из временного файла: $tempVideoPath")
        
        val videoFile = File(tempVideoPath)
        if (!videoFile.exists()) {
            throw IllegalArgumentException("Временный файл не существует: $tempVideoPath")
        }
        
        try {
            val galleryUri = saveToGallery(videoFile)
            // Удаляем временный файл после успешного сохранения
            videoFile.delete()
            Log.d(TAG, "Временный файл удален: $tempVideoPath")
            galleryUri
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении в галерею", e)
            throw e
        }
    }
}