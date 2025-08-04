package com.example.clipcraft.domain.usecase

import android.util.Log
import com.example.clipcraft.data.remote.ApiMapper
import com.example.clipcraft.data.remote.ClipCraftApiService
import com.example.clipcraft.models.*
import com.example.clipcraft.services.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.example.clipcraft.utils.ProcessingMessageProvider

class ProcessVideosUseCase @Inject constructor(
    private val videoAnalyzer: VideoAnalyzerService,
    private val transcriptionService: TranscriptionService,
    private val videoEditor: VideoEditorService,
    private val apiService: ClipCraftApiService,
    private val messageProvider: ProcessingMessageProvider
) {
    private val TAG = "ProcessVideosUseCase"

    suspend operator fun invoke(
        selectedVideos: List<SelectedVideo>,
        userCommand: String,
        exportSettings: ExportSettings = ExportSettings(),
        editingState: EditingState = EditingState(),
        videoAnalysesMap: Map<String, VideoAnalysis>? = null
    ): Flow<ProcessingState> = flow {

        try {
            val currentVideoAnalyses = mutableListOf<VideoAnalysis>()
            val currentVideoMap = mutableMapOf<String, SelectedVideo>()

            if (editingState.mode == ProcessingMode.EDIT && videoAnalysesMap != null) {
                // В режиме редактирования используем уже существующий анализ
                emit(ProcessingState.ProgressUpdate(messageProvider.getEditModeMessage()))
                Log.d(TAG, "Режим редактирования: используем сохраненный анализ видео.")
                selectedVideos.forEach { video ->
                    val analysis = videoAnalysesMap[video.fileName]
                    if (analysis != null) {
                        currentVideoAnalyses.add(analysis)
                        currentVideoMap[video.fileName] = video
                    } else {
                        Log.e(TAG, "Ошибка: Анализ для видео '${video.fileName}' не найден в режиме редактирования.")
                        emit(ProcessingState.Error(messageProvider.getVideoAnalysisNotFoundError()))
                        return@flow
                    }
                }
            } else {
                // Для нового видео или если анализ не предоставлен, выполняем полный анализ
                emit(ProcessingState.ProgressUpdate(messageProvider.getAnalyzingVideosMessage()))
                Log.d(TAG, "Начало анализа видео. Режим: ${editingState.mode}")

                selectedVideos.forEachIndexed { index, video ->
                    emit(ProcessingState.ProgressUpdate(messageProvider.getVideoProgressMessage(index + 1, selectedVideos.size)))
                    Log.d(TAG, "Обработка видео ${index + 1}/${selectedVideos.size}: fileName='${video.fileName}', path='${video.path}'")

                    // Анализируем видео с определением сцен
                    var analysis = videoAnalyzer.analyzeVideoWithScenes(video)
                    emit(ProcessingState.ProgressUpdate(messageProvider.getVideoAnalyzedMessage(index + 1)))
                    Log.d(TAG, "Анализ сцен для ${video.fileName} завершен. Найдено ${analysis.scenes.size} сцен.")

                    // Если есть аудио, транскрибируем
                    if (video.hasAudio) {
                        Log.d(TAG, "Начало транскрибации для ${video.fileName}...")

                        val transcription = transcriptionService.transcribeVideo(video.uri) { msg ->
                            Log.d(TAG, "Прогресс транскрипции для ${video.fileName}: $msg")
                        }

                        if (transcription.isNotEmpty()) {
                            // Собираем весь текст транскрипции
                            val fullText = transcription.joinToString(" ") { it.text }
                            val truncatedText = if (fullText.length > 100) {
                                fullText.take(97) + "..."
                            } else {
                                fullText
                            }

                            emit(ProcessingState.ProgressUpdate(messageProvider.getSpeechFoundMessage(index + 1, truncatedText)))
                            Log.d(TAG, "Транскрибировано ${transcription.size} сегментов для ${video.fileName}")

                            // Добавляем транскрипцию к каждой сцене
                            val updatedScenes = analysis.scenes.map { scene ->
                                val sceneTranscription = transcription.filter { segment ->
                                    segment.start >= scene.startTime && segment.end <= scene.endTime
                                }
                                scene.copy(transcription = sceneTranscription)
                            }
                            analysis = analysis.copy(scenes = updatedScenes)
                            Log.d(TAG, "Транскрипция успешно добавлена к сценам ${video.fileName}.")
                        } else {
                            // Не показываем сообщение если речь не обнаружена
                            Log.d(TAG, "Нет транскрипции для ${video.fileName} (возможно, нет голоса или возникла ошибка).")
                        }
                    } else {
                        // Не показываем сообщение если нет звука
                        Log.d(TAG, "Видео ${video.fileName} без звука - пропускаем транскрибацию.")
                    }

                    currentVideoAnalyses.add(analysis)
                    currentVideoMap[video.fileName] = video

                    Log.d(TAG, "Добавлено в currentVideoMap: key='${video.fileName}' -> video.uri=${video.uri}")
                }
            }

            // Проверяем, что есть хотя бы одна сцена с кадром для отправки на сервер
            val hasValidScenes = currentVideoAnalyses.any {
                it.scenes.any { scene -> !scene.frameBase64.isNullOrEmpty() }
            }

            if (!hasValidScenes) {
                emit(ProcessingState.Error(messageProvider.getCannotExtractFramesError()))
                Log.e(TAG, "Ошибка: Не удалось извлечь кадры из видео для анализа сервером.")
                return@flow
            }
            emit(ProcessingState.ProgressUpdate(messageProvider.getAllVideosReadyMessage()))
            Log.d(TAG, "Все видео проанализированы и готовы к отправке на сервер ClipCraft.")

            // 2. Отправка на сервер ClipCraft
            emit(ProcessingState.ProgressUpdate(messageProvider.getAnalyzingContentMessage()))
            Log.d(TAG, "Проверка доступности основного сервера ClipCraft...")

            try {
                performHealthCheck()
                emit(ProcessingState.ProgressUpdate(messageProvider.getAnalysisCompleteMessage()))
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при проверке доступности основного сервера ClipCraft", e)
                emit(ProcessingState.Error(messageProvider.getCannotAnalyzeVideoError()))
                return@flow
            }

            val apiRequest = ApiMapper.toApiRequest(
                userCommand = userCommand,
                videoAnalyses = currentVideoAnalyses,
                editingState = editingState
            )
            Log.d(TAG, "Сформирован запрос к ClipCraft API. UserCommand: '$userCommand', Видео: ${currentVideoAnalyses.size}, Режим: ${editingState.mode}")

            emit(ProcessingState.ProgressUpdate(messageProvider.getCreatingPlanMessage()))
            val apiResponse = apiService.analyzeVideos(apiRequest)
            emit(ProcessingState.ProgressUpdate(messageProvider.getPlanReadyMessage()))
            Log.d(TAG, "Ответ от ClipCraft API получен успешно.")

            val editPlan = ApiMapper.fromApiResponse(apiResponse)

            // Показываем детали плана монтажа
            val planSummary = "Будет создано видео из ${editPlan.finalEdit.size} фрагментов"
            emit(ProcessingState.ProgressUpdate(messageProvider.getPlanSummaryMessage(planSummary)))

            // Если есть notes в плане, показываем их
            editPlan.finalEdit.forEach { segment ->
                segment.notes?.let { notes ->
                    if (notes.isNotBlank()) {
                        emit(ProcessingState.ProgressUpdate(messageProvider.getPlanNotesMessage(notes)))
                    }
                }
            }

            Log.d(TAG, "План монтажа десериализован. Количество сегментов в плане: ${editPlan.finalEdit.size}.")

            // Проверяем план монтажа
            if (editPlan.finalEdit.isEmpty()) {
                emit(ProcessingState.Error(messageProvider.getEmptyEditPlanError()))
                Log.e(TAG, "Ошибка: Сервер вернул пустой план монтажа.")
                return@flow
            }

            // 3. Монтаж видео локально
            emit(ProcessingState.ProgressUpdate(messageProvider.getStartingEditMessage()))
            Log.d(TAG, "Начало локального монтажа видео.")

            val outputPath = videoEditor.executeEditPlan(
                editPlan = editPlan,
                videoMap = currentVideoMap,
                exportSettings = exportSettings,
                onProgress = { progress ->
                    val percentInt = (progress * 100).toInt()
                    Log.d(TAG, "Прогресс монтажа: $percentInt%")
                    // Не можем вызвать emit здесь, так как это не suspend функция
                    // Прогресс будет логироваться, но не отображаться в UI
                }
            )
            emit(ProcessingState.ProgressUpdate(messageProvider.getVideoReadyMessage()))
            Log.d(TAG, "Локальный монтаж видео завершен. Выходной путь: $outputPath")

            // 4. Успех
            // Важно: сохраняем анализ видео для будущего редактирования
            val videoAnalysesMap = currentVideoAnalyses.associateBy { it.fileName }
            emit(ProcessingState.Success(outputPath, editPlan, videoAnalysesMap))
            Log.d(TAG, "Процесс обработки видео успешно завершен. Результат: $outputPath")

        } catch (e: retrofit2.HttpException) {
            val errorMessage = when (e.code()) {
                500 -> messageProvider.getProcessingLaterError()
                404 -> messageProvider.getFeatureUnavailableError()
                400 -> messageProvider.getInvalidParametersError()
                else -> messageProvider.getNetworkError(e.code())
            }
            Log.e(TAG, "HTTP ошибка при обработке видео: Код ${e.code()}, Сообщение: ${e.message()}", e)
            emit(ProcessingState.Error(errorMessage))
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Ошибка: Нет подключения к интернету или недоступен хост ClipCraft API.", e)
            emit(ProcessingState.Error(messageProvider.getNoInternetError()))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Ошибка: Превышено время ожидания ответа от сервера ClipCraft API.", e)
            emit(ProcessingState.Error(messageProvider.getTimeoutError()))
        } catch (e: Exception) {
            val errorMessage = e.message ?: messageProvider.getUnknownError()
            Log.e(TAG, "Неизвестная ошибка при обработке видео: $errorMessage", e)
            emit(ProcessingState.Error(errorMessage))
        }
    }

    private suspend fun performHealthCheck() {
        val health = withContext(Dispatchers.IO) { apiService.checkHealth() }
        if (health.status != "healthy") {
            Log.e(TAG, "Основной сервер ClipCraft недоступен: Статус - ${health.status}")
            throw Exception(messageProvider.getServerUnavailableError(health.status))
        }
        Log.d(TAG, "Основной сервер ClipCraft доступен. Статус: ${health.status}")
    }
}