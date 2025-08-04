package com.example.clipcraft.utils

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Менеджер для управления временными файлами приложения
 */
@Singleton
class TemporaryFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TemporaryFileManager"
    }

    // Множество для отслеживания временных файлов
    private val trackedFiles = mutableSetOf<String>()

    /**
     * Зарегистрировать временный файл для отслеживания
     */
    fun registerTemporaryFile(path: String) {
        synchronized(trackedFiles) {
            trackedFiles.add(path)
            Log.d(TAG, "Registered temporary file: $path")
        }
    }
    
    /**
     * Разрегистрировать временный файл
     */
    fun unregisterTemporaryFile(path: String) {
        synchronized(trackedFiles) {
            trackedFiles.remove(path)
            Log.d(TAG, "Unregistered temporary file: $path")
        }
    }

    /**
     * Удалить конкретный временный файл
     */
    suspend fun deleteTemporaryFile(path: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                val deleted = file.delete()
                if (deleted) {
                    Log.d(TAG, "Deleted temporary file: $path")
                }
                synchronized(trackedFiles) {
                    trackedFiles.remove(path)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting temporary file: $path", e)
            }
        }
    }

    /**
     * Удалить все отслеживаемые временные файлы
     */
    suspend fun cleanupAllTemporaryFiles() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Cleaning up all temporary files")
            
            // Удаляем отслеживаемые файлы
            synchronized(trackedFiles) {
                val filesToDelete = trackedFiles.toList()
                filesToDelete.forEach { path ->
                    try {
                        val file = File(path)
                        val deleted = file.delete()
                        if (deleted) {
                            Log.d(TAG, "Deleted temporary file: $path")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting file: $path", e)
                    }
                }
                trackedFiles.clear()
            }
        }
        
        // Очищаем известные директории с временными файлами
        cleanupTemporaryDirectories()
    }

    /**
     * Очистить временные директории по паттернам имен
     */
    private suspend fun cleanupTemporaryDirectories() {
        withContext(Dispatchers.IO) {
            // Очищаем cache/temp_videos
            cleanupDirectory("temp_videos") { fileName ->
                fileName.startsWith("temp_") ||
                fileName.startsWith("edited_") ||
                fileName.startsWith("output_") ||
                fileName.startsWith("export_")
            }
            
            // Очищаем cache/video_editor_temp
            cleanupDirectory("video_editor_temp") { true }
            
            // Очищаем cache/thumbnails
            cleanupDirectory("thumbnails") { true }
        }
    }

    /**
     * Очистить конкретную директорию
     */
    private fun cleanupDirectory(dirName: String, fileFilter: (String) -> Boolean) {
        try {
            val dir = File(context.cacheDir, dirName)
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (fileFilter(file.name)) {
                        val deleted = if (file.isDirectory) {
                            file.deleteRecursively()
                        } else {
                            file.delete()
                        }
                        if (deleted) {
                            Log.d(TAG, "Deleted $dirName file: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning $dirName directory", e)
        }
    }

    /**
     * Проверить, является ли путь временным файлом
     */
    fun isTemporaryFile(path: String): Boolean {
        return path.contains("/cache/") ||
                path.contains("/temp/") ||
                path.contains("/temp_videos/") ||
                path.contains("/video_editor_temp/") ||
                path.contains("/output_") ||
                path.contains("/edited_") ||
                path.contains("/export_") ||
                path.contains("/temp_")
    }
    
    /**
     * Получить директорию для временных файлов видеоредактора
     */
    fun getVideoEditorTempDir(): String {
        val dir = File(context.cacheDir, "video_editor_temp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir.absolutePath
    }
}