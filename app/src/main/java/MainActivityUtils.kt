package com.example.clipcraft

import android.content.Context
import android.net.Uri
import java.io.File
import com.example.clipcraft.models.*

/**
 * Конвертирует Uri в временный файл
 */
suspend fun Context.uriToTempFile(uri: Uri): File? {
    return try {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val tempFile = File.createTempFile("temp_video_", ".mp4", cacheDir)
        tempFile.outputStream().use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}