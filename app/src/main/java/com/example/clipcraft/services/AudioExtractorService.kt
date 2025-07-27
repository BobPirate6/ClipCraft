package com.example.clipcraft.services

import android.content.Context
import android.media.*
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioExtractorService @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "AudioExtractorService"
        private const val TIMEOUT_US = 10_000L
        private const val BUFFER_SIZE = 1 * 1024 * 1024 // 1 MB
    }

    /**
     * Извлекает аудио‐трек и сохраняет его в контейнере **m4a** (формат, который принимает Whisper).
     * Возвращает файл *.m4a* или null, если в ролике нет аудио.
     */
    suspend fun extractAudio(videoUri: Uri): File? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var audioTrackIndex = -1

        try {
            extractor.setDataSource(context, videoUri, null)

            // Находим первый аудио-трек
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime  = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    Log.d(TAG, "Найден аудио-трек: $mime (index=$i)")
                    break
                }
            }
            if (audioTrackIndex == -1) {
                Log.d(TAG, "Аудио-трек не найден")
                return@withContext null
            }

            // Готовим выходной файл *.m4a*
            val outFile = File(
                context.cacheDir,
                "audio_${System.currentTimeMillis()}.m4a"
            )
            if (outFile.exists()) outFile.delete()

            // Перемухируем трек в MP4-контейнер
            muxTrackToM4A(extractor, audioTrackIndex, outFile)

            Log.d(
                TAG,
                "Аудио успешно извлечено: ${outFile.absolutePath}, размер: ${outFile.length()} байт"
            )
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка извлечения аудио", e)
            null
        } finally {
            extractor.release()
        }
    }

    /** Перекладка (remux) одного дорожки в новый MP4-контейнер без перекодирования. */
    private fun muxTrackToM4A(
        extractor: MediaExtractor,
        trackIndex: Int,
        outFile: File
    ) {
        val format = extractor.getTrackFormat(trackIndex)
        extractor.selectTrack(trackIndex)

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val dstTrackIndex = muxer.addTrack(format)
        muxer.start()

        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val info   = MediaCodec.BufferInfo()

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            info.size                = sampleSize
            info.presentationTimeUs  = extractor.sampleTime
            info.flags               = extractor.sampleFlags

            muxer.writeSampleData(dstTrackIndex, buffer, info)
            extractor.advance()
        }

        muxer.stop()
        muxer.release()
    }

    /** Длительность ролика в секундах (вспомогательно). */
    suspend fun getVideoDuration(uri: Uri): Float = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            (retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L) / 1000f
        } finally {
            retriever.release()
        }
    }
}
