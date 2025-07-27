package com.example.clipcraft.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.clipcraft.models.*
import com.example.clipcraft.services.VideoEditingService
import com.example.clipcraft.services.VideoInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoEditingService: VideoEditingService,
    private val savedStateHandle: SavedStateHandle,
    private val gson: Gson
) : ViewModel() {
    
    companion object {
        private const val TAG = "VideoEditorViewModel"
        private const val MAX_HISTORY_SIZE = 10
        
        // Ключи для SavedStateHandle
        private const val KEY_TIMELINE_SEGMENTS = "timeline_segments"
        private const val KEY_ORIGINAL_PLAN = "original_plan"
        private const val KEY_ORIGINAL_ANALYSES = "original_analyses"
        private const val KEY_EDIT_HISTORY = "edit_history"
        private const val KEY_HISTORY_INDEX = "history_index"
    }
    
    private val _editorState = MutableStateFlow(VideoEditorState())
    val editorState: StateFlow<VideoEditorState> = _editorState.asStateFlow()
    
    // Удобные свойства для UI
    val canUndo: Boolean get() = _historyIndex >= 0
    val canRedo: Boolean get() = _historyIndex < _editHistory.size - 1
    
    private val _timelineState = MutableStateFlow(TimelineState())
    val timelineState: StateFlow<TimelineState> = _timelineState.asStateFlow()
    
    private val _editHistory = mutableListOf<EditAction>()
    private var _historyIndex = -1
    
    // Сохраняем оригинальные данные для возможности сброса
    private var _originalPlan: EditPlan? = null
    private var _originalVideoAnalyses: Map<String, VideoAnalysis>? = null
    private var _originalSelectedVideos: List<SelectedVideo> = emptyList()
    
    init {
        Log.d(TAG, "VideoEditorViewModel init called")
        restoreState()
        Log.d(TAG, "After restoreState: segments=${_timelineState.value.segments.size}")
    }
    
    /**
     * Инициализация редактора с планом монтажа
     */
    fun initializeWithEditPlan(
        editPlan: EditPlan,
        videoAnalyses: Map<String, VideoAnalysis>,
        selectedVideos: List<SelectedVideo> = emptyList()
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Initializing editor with ${editPlan.finalEdit.size} segments")
                Log.d("videoeditorclipcraft", "VideoEditorViewModel.initializeWithEditPlan called with ${editPlan.finalEdit.size} segments")
                
                // Проверяем, есть ли уже сегменты
                if (_timelineState.value.segments.isNotEmpty()) {
                    Log.d(TAG, "Editor already initialized with ${_timelineState.value.segments.size} segments, skipping re-initialization")
                    Log.d("videoeditorclipcraft", "Segments already loaded: ${_timelineState.value.segments.size}")
                    // Обновляем состояние редактора чтобы UI мог видеть сегменты
                    _editorState.value = _editorState.value.copy(
                        originalPlan = editPlan,
                        timelineState = _timelineState.value
                    )
                    return@launch
                }
                
                // Если план пустой, это ручное редактирование
                if (editPlan.finalEdit.isEmpty() && selectedVideos.isNotEmpty()) {
                    Log.d(TAG, "Manual editing mode - creating segments from selected videos")
                    initializeManualEdit(selectedVideos)
                    return@launch
                }
                
                // Логируем детали плана
                editPlan.finalEdit.forEachIndexed { index, segment ->
                    Log.d("videoeditorclipcraft", "Segment $index: sourceVideo=${segment.sourceVideo}, startTime=${segment.startTime}, endTime=${segment.endTime}")
                }
                
                // Сохраняем оригинальные данные при первой инициализации
                if (_originalPlan == null) {
                    _originalPlan = editPlan
                    _originalVideoAnalyses = videoAnalyses
                    _originalSelectedVideos = selectedVideos
                }
                
                // Создаем маппинг имен файлов на URI
                val videoUriMap = selectedVideos.associateBy { it.fileName }
                Log.d("videoeditorclipcraft", "Created video URI map with ${videoUriMap.size} entries")
                videoUriMap.forEach { (fileName, video) ->
                    Log.d("videoeditorclipcraft", "Map: $fileName -> ${video.uri}")
                }
                
                // Конвертируем план в сегменты для таймлайна
                val segments = editPlan.finalEdit.mapNotNull { editSegment ->
                    val sourceVideoFileName = editSegment.sourceVideo
                    Log.d("videoeditorclipcraft", "Processing segment: sourceVideoFileName=$sourceVideoFileName")
                    
                    // Убираем расширение .mp4 для поиска в мапе
                    val fileNameWithoutExtension = sourceVideoFileName.removeSuffix(".mp4")
                    Log.d("videoeditorclipcraft", "Looking for video with name: $fileNameWithoutExtension")
                    
                    // Пытаемся найти видео по имени файла
                    val selectedVideo = videoUriMap[fileNameWithoutExtension]
                    if (selectedVideo == null) {
                        Log.e("videoeditorclipcraft", "Could not find video for filename: $fileNameWithoutExtension")
                        // Проверяем, может быть это полный путь
                        val videoFile = File(sourceVideoFileName)
                        if (!videoFile.exists()) {
                            Log.e("videoeditorclipcraft", "Video file does not exist: $sourceVideoFileName")
                            return@mapNotNull null
                        }
                        val videoUri = Uri.fromFile(videoFile)
                        val videoPath = sourceVideoFileName
                        Log.d("videoeditorclipcraft", "Using full path as URI: $videoUri")
                    } else {
                        Log.d("videoeditorclipcraft", "Found video in map: ${selectedVideo.uri}")
                    }
                    
                    val videoUri = selectedVideo?.uri ?: Uri.fromFile(File(sourceVideoFileName))
                    val videoPath = selectedVideo?.uri?.path ?: sourceVideoFileName
                    Log.d("videoeditorclipcraft", "Final URI: $videoUri, path: $videoPath")
                    
                    // Генерируем превью для сегмента
                    val thumbnails = try {
                        Log.d("videoeditorclipcraft", "Generating thumbnails for URI: $videoUri")
                        val generatedThumbnails = videoEditingService.generateThumbnails(
                            videoUri = videoUri,
                            startTime = editSegment.startTime,
                            endTime = editSegment.endTime,
                            count = 5
                        )
                        Log.d("videoeditorclipcraft", "Generated ${generatedThumbnails.size} thumbnails")
                        generatedThumbnails
                    } catch (e: Exception) {
                        Log.e("videoeditorclipcraft", "Failed to generate thumbnails", e)
                        emptyList()
                    }
                    
                    Log.d("videoeditorclipcraft", "Generated ${thumbnails.size} thumbnails")
                    
                    // Получаем информацию о видео для определения его полной длительности
                    val videoInfo = try {
                        videoEditingService.getVideoInfo(videoUri)
                    } catch (e: Exception) {
                        Log.e("videoeditorclipcraft", "Failed to get video info", e)
                        VideoInfo(duration = editSegment.endTime, width = 0, height = 0, frameRate = 30f)
                    }
                    
                    val segment = VideoSegment(
                        sourceVideoUri = videoUri,
                        sourceVideoPath = videoPath,
                        sourceFileName = fileNameWithoutExtension,
                        originalDuration = videoInfo.duration,
                        inPoint = editSegment.startTime,
                        outPoint = editSegment.endTime,
                        timelinePosition = 0f, // Будет рассчитана позже
                        thumbnails = thumbnails
                    )
                    
                    Log.d("videoeditorclipcraft", "Created segment: id=${segment.id}, duration=${segment.duration}")
                    segment
                }
                
                Log.d("videoeditorclipcraft", "Total segments created: ${segments.size}")
                
                val totalDuration = segments.sumOf { it.duration.toDouble() }.toFloat()
                Log.d("videoeditorclipcraft", "Total duration: $totalDuration seconds")
                
                // At 100% zoom, all segments should fit within timeline
                // Default zoom is 1.0 (100%)
                val initialZoom = 1.0f
                Log.d("videoeditorclipcraft", "Initial zoom set to: $initialZoom (100%)")
                
                _timelineState.value = TimelineState(
                    segments = segments,
                    totalDuration = totalDuration,
                    zoomLevel = initialZoom
                )
                
                _editorState.value = _editorState.value.copy(
                    originalPlan = editPlan,
                    timelineState = _timelineState.value
                )
                
                Log.d("videoeditorclipcraft", "Timeline state updated with ${_timelineState.value.segments.size} segments")
                Log.d("videoeditorclipcraft", "Editor state updated")
                
                // Создаем первую временную версию видео
                if (segments.isNotEmpty()) {
                    Log.d("videoeditorclipcraft", "Creating initial preview video")
                    updateTempVideo()
                }
                
                // Сохраняем состояние после инициализации
                saveState()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing editor", e)
                Log.e("videoeditorclipcraft", "Failed to initialize editor", e)
            }
        }
    }
    
    /**
     * Инициализация для ручного редактирования
     */
    private suspend fun initializeManualEdit(selectedVideos: List<SelectedVideo>) {
        try {
            Log.d(TAG, "Initializing manual edit with ${selectedVideos.size} videos")
            
            // Сохраняем оригинальные данные
            _originalSelectedVideos = selectedVideos
            _originalPlan = EditPlan(emptyList())
            _originalVideoAnalyses = emptyMap()
            
            // Создаем сегменты из всех выбранных видео
            val segments = selectedVideos.mapIndexedNotNull { index, selectedVideo ->
                try {
                    val videoUri = selectedVideo.uri
                    val videoInfo = videoEditingService.getVideoInfo(videoUri)
                    
                    // Генерируем превью
                    val thumbnails = try {
                        videoEditingService.generateThumbnails(
                            videoUri = videoUri,
                            startTime = 0f,
                            endTime = videoInfo.duration,
                            count = 5
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to generate thumbnails for ${selectedVideo.fileName}", e)
                        emptyList()
                    }
                    
                    VideoSegment(
                        sourceVideoUri = videoUri,
                        sourceVideoPath = selectedVideo.uri.path ?: "",
                        sourceFileName = selectedVideo.fileName,
                        originalDuration = videoInfo.duration,
                        inPoint = 0f,
                        outPoint = videoInfo.duration,
                        timelinePosition = 0f, // Будет рассчитана позже
                        thumbnails = thumbnails
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create segment for ${selectedVideo.fileName}", e)
                    null
                }
            }
            
            if (segments.isEmpty()) {
                Log.e(TAG, "No segments created for manual edit")
                _editorState.value = _editorState.value.copy(
                    isProcessing = false
                )
                return
            }
            
            val totalDuration = segments.sumOf { it.duration.toDouble() }.toFloat()
            Log.d(TAG, "Created ${segments.size} segments with total duration: $totalDuration")
            
            _timelineState.value = TimelineState(
                segments = segments,
                totalDuration = totalDuration,
                zoomLevel = 1.0f
            )
            
            _editorState.value = _editorState.value.copy(
                originalPlan = _originalPlan,
                timelineState = _timelineState.value,
                isProcessing = false
            )
            
            // Создаем первую временную версию видео
            updateTempVideo()
            
            // Сохраняем состояние
            saveState()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeManualEdit", e)
            _editorState.value = _editorState.value.copy(
                isProcessing = false
            )
        }
    }
    
    /**
     * Выбор сегмента
     */
    fun selectSegment(segmentId: String?) {
        Log.d("videoeditorclipcraft", "VideoEditorViewModel.selectSegment called with segmentId: $segmentId")
        _timelineState.update { state ->
            state.copy(selectedSegmentId = segmentId)
        }
        Log.d("videoeditorclipcraft", "Selected segment updated to: ${_timelineState.value.selectedSegmentId}")
    }
    
    /**
     * Начало редактирования обрезки
     */
    fun startTrimming(segmentId: String, isStart: Boolean) {
        _timelineState.update { state ->
            state.copy(
                editingSegmentId = segmentId,
                isEditingStart = isStart,
                isEditingEnd = !isStart
            )
        }
    }
    
    /**
     * Окончание редактирования обрезки
     */
    fun endTrimming() {
        _timelineState.update { state ->
            state.copy(
                editingSegmentId = null,
                isEditingStart = false,
                isEditingEnd = false
            )
        }
        // Больше не обновляем превью автоматически - используем CompositeVideoPlayer
    }
    
    /**
     * Применение изменений после завершения перетаскивания манипулятора
     */
    fun applyTrimChanges(segmentId: String, deltaTime: Float, isStart: Boolean) {
        if (isStart) {
            trimSegmentStart(segmentId, deltaTime)
        } else {
            trimSegmentEnd(segmentId, deltaTime)
        }
        endTrimming()
    }
    
    /**
     * Обрезка начала сегмента
     */
    fun trimSegmentStart(segmentId: String, deltaTime: Float) {
        Log.d("videoeditorclipcraft", "trimSegmentStart: segmentId=$segmentId, deltaTime=$deltaTime")
        val segment = _timelineState.value.segments.find { it.id == segmentId } ?: return
        
        Log.d("videoeditorclipcraft", "trimSegmentStart: Current segment - originalStart=${segment.originalStartTime}, originalEnd=${segment.originalEndTime}, start=${segment.startTime}, end=${segment.endTime}")
        
        // Вычисляем новое время начала
        val newStartTime = (segment.startTime + deltaTime).coerceIn(
            segment.originalStartTime,
            segment.endTime - 0.5f // Минимальная длина 0.5 сек
        )
        
        // ВАЖНО: Конец остается неизменным при обрезке с начала!
        val newEndTime = segment.endTime
        
        Log.d("videoeditorclipcraft", "trimSegmentStart: oldStart=${segment.startTime}, newStart=$newStartTime, oldEnd=${segment.endTime}, newEnd=$newEndTime")
        
        if (newStartTime != segment.startTime) {
            recordAction(EditAction.TrimSegment(
                segmentId = segmentId,
                oldStartTime = segment.startTime,
                oldEndTime = segment.endTime,
                newStartTime = newStartTime,
                newEndTime = newEndTime
            ))
            
            updateSegment(segmentId) { it.copy(inPoint = newStartTime) }
            Log.d("videoeditorclipcraft", "trimSegmentStart: segment updated with new startTime=$newStartTime, endTime unchanged")
        }
    }
    
    /**
     * Обрезка конца сегмента
     */
    fun trimSegmentEnd(segmentId: String, deltaTime: Float) {
        Log.d("videoeditorclipcraft", "trimSegmentEnd: segmentId=$segmentId, deltaTime=$deltaTime")
        val segment = _timelineState.value.segments.find { it.id == segmentId } ?: return
        val newEndTime = (segment.endTime + deltaTime).coerceIn(
            segment.startTime + 0.5f, // Минимальная длина 0.5 сек
            segment.originalEndTime
        )
        
        Log.d("videoeditorclipcraft", "trimSegmentEnd: oldEndTime=${segment.endTime}, newEndTime=$newEndTime")
        
        if (newEndTime != segment.endTime) {
            recordAction(EditAction.TrimSegment(
                segmentId = segmentId,
                oldStartTime = segment.startTime,
                oldEndTime = segment.endTime,
                newStartTime = segment.startTime,
                newEndTime = newEndTime
            ))
            
            updateSegment(segmentId) { it.copy(outPoint = newEndTime) }
            Log.d("videoeditorclipcraft", "trimSegmentEnd: segment updated")
        }
    }
    
    /**
     * Удаление сегмента
     */
    fun deleteSegment(segmentId: String) {
        val segments = _timelineState.value.segments
        val index = segments.indexOfFirst { it.id == segmentId }
        if (index != -1) {
            val segment = segments[index]
            recordAction(EditAction.RemoveSegment(segment, index))
            
            _timelineState.update { state ->
                val filteredSegments = state.segments.filter { it.id != segmentId }
                val newTotalDuration = filteredSegments.sumOf { it.duration.toDouble() }.toFloat()
                
                state.copy(
                    segments = filteredSegments,
                    selectedSegmentId = null,
                    totalDuration = newTotalDuration
                )
            }
            
            // Больше не обновляем превью автоматически
        }
    }
    
    /**
     * Добавление нового сегмента
     */
    fun addSegment(videoUri: Uri, insertPosition: InsertPosition) {
        viewModelScope.launch {
            try {
                // Получаем информацию о видео
                val videoInfo = videoEditingService.getVideoInfo(videoUri)
                val thumbnails = videoEditingService.generateThumbnails(
                    videoUri = videoUri,
                    startTime = 0f,
                    endTime = videoInfo.duration,
                    count = 5
                )
                
                val segment = VideoSegment(
                    sourceVideoUri = videoUri,
                    sourceVideoPath = videoUri.path ?: "",
                    sourceFileName = videoUri.lastPathSegment ?: "video",
                    originalDuration = videoInfo.duration,
                    inPoint = 0f,
                    outPoint = videoInfo.duration,
                    timelinePosition = 0f, // Будет рассчитана при добавлении
                    thumbnails = thumbnails
                )
                
                val actualIndex = when (insertPosition.side) {
                    InsertSide.BEFORE -> insertPosition.index
                    InsertSide.AFTER -> insertPosition.index + 1
                }
                
                recordAction(EditAction.AddSegment(segment, actualIndex))
                
                _timelineState.update { state ->
                    val newSegments = state.segments.toMutableList()
                    newSegments.add(actualIndex, segment)
                    state.copy(segments = newSegments)
                }
                
                updateTempVideo()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding segment", e)
            }
        }
    }
    
    /**
     * Перемещение сегмента
     */
    fun moveSegment(segmentId: String, fromIndex: Int, toIndex: Int) {
        if (fromIndex != toIndex) {
            recordAction(EditAction.MoveSegment(segmentId, fromPosition = fromIndex, toPosition = toIndex))
            
            _timelineState.update { state ->
                val segments = state.segments.toMutableList()
                val segment = segments.removeAt(fromIndex)
                segments.add(toIndex, segment)
                state.copy(segments = segments)
            }
            
            // Больше не обновляем превью автоматически
        }
    }
    
    /**
     * Слипание сегментов после завершения перетаскивания или редактирования
     */
    fun snapSegmentsTogether() {
        Log.d("videoeditorclipcraft", "snapSegmentsTogether: Starting segments snapping")
        
        _timelineState.update { state ->
            val segments = state.segments.toMutableList()
            
            // Пересчитываем позиции таймлайна для всех сегментов
            var currentPosition = 0f
            segments.forEachIndexed { index, segment ->
                // Обновляем позицию сегмента на таймлайне
                segments[index] = segment.copy(timelinePosition = currentPosition)
                currentPosition += segment.duration
                
                Log.d("videoeditorclipcraft", "snapSegmentsTogether: Segment ${segment.id} at position ${segments[index].timelinePosition}, duration ${segment.duration}")
            }
            
            state.copy(segments = segments)
        }
    }
    
    /**
     * Обновление позиции playhead
     */
    fun updatePlayheadPosition(position: Float) {
        _timelineState.update { state ->
            state.copy(currentPosition = position.coerceIn(0f, state.totalDuration))
        }
    }
    
    /**
     * Переключение воспроизведения/паузы
     */
    fun togglePlayback() {
        _timelineState.update { state ->
            state.copy(isPlaying = !state.isPlaying)
        }
    }
    
    /**
     * Установка состояния воспроизведения (для синхронизации с плеером)
     */
    fun setPlayingState(isPlaying: Boolean) {
        _timelineState.update { state ->
            state.copy(isPlaying = isPlaying)
        }
    }
    
    /**
     * Обновление превью видео после изменений
     */
    fun updatePreviewVideo() {
        Log.d("videoeditorclipcraft", "updatePreviewVideo called")
        viewModelScope.launch {
            updateTempVideo()
        }
    }
    
    /**
     * Изменение уровня зума
     */
    fun updateZoomLevel(zoom: Float) {
        _timelineState.update { state ->
            state.copy(zoomLevel = zoom.coerceIn(0.1f, 5f))
        }
    }
    
    private var lastZoomClickTime = 0L
    private val DOUBLE_CLICK_TIMEOUT = 300L
    
    /**
     * Обработка нажатия на кнопку зума (двойное нажатие сбрасывает зум)
     */
    fun handleZoomButtonClick() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastZoomClickTime < DOUBLE_CLICK_TIMEOUT) {
            // Double click detected - reset zoom to 100%
            updateZoomLevel(1.0f)
            Log.d(TAG, "Double click detected - zoom reset to 100%")
        }
        lastZoomClickTime = currentTime
    }
    
    /**
     * Undo
     */
    fun undo() {
        if (_historyIndex >= 0 && _historyIndex < _editHistory.size) {
            val action = _editHistory[_historyIndex]
            revertAction(action)
            _historyIndex--
            
            // Обновляем состояние редактора с новой историей
            _editorState.update { state ->
                state.copy(
                    hasUnsavedChanges = true,
                    editHistory = EditHistoryState(
                        actions = _editHistory,
                        currentIndex = _historyIndex,
                        maxHistorySize = MAX_HISTORY_SIZE
                    )
                )
            }
            
            // Больше не обновляем превью автоматически
        }
    }
    
    /**
     * Redo
     */
    fun redo() {
        if (_historyIndex < _editHistory.size - 1) {
            _historyIndex++
            val action = _editHistory[_historyIndex]
            applyAction(action)
            
            // Обновляем состояние редактора с новой историей
            _editorState.update { state ->
                state.copy(
                    hasUnsavedChanges = true,
                    editHistory = EditHistoryState(
                        actions = _editHistory,
                        currentIndex = _historyIndex,
                        maxHistorySize = MAX_HISTORY_SIZE
                    )
                )
            }
            
            // Больше не обновляем превью автоматически
        }
    }
    
    /**
     * Применить голосовую команду к монтажу
     */
    suspend fun applyVoiceCommand(
        command: String,
        currentEditPlan: EditPlan,
        videoAnalyses: Map<String, VideoAnalysis>,
        selectedVideos: List<SelectedVideo>,
        mainViewModel: MainViewModel
    ) {
        Log.d(TAG, "Applying voice command: $command")
        
        // Сохраняем команду для обработки на главном экране
        mainViewModel.updateCommand(command)
        
        // Проверяем, был ли видео смонтирован вручную (пустой план)
        val isManuallyEdited = currentEditPlan.finalEdit.isEmpty() || 
                              videoAnalyses.isEmpty()
        
        if (isManuallyEdited) {
            // Если видео было смонтировано вручную, запускаем полный процесс с AI
            Log.d(TAG, "Video was manually edited, starting full AI processing")
            mainViewModel.processVideos()
        } else {
            // Если видео уже обработано AI, используем существующий план
            Log.d(TAG, "Video was AI processed, starting editing with existing plan")
            mainViewModel.updateEditingState(EditingState(
                mode = ProcessingMode.EDIT,
                originalCommand = mainViewModel.userCommand.value,
                editCommand = command,
                previousPlan = currentEditPlan,
                originalVideoAnalyses = videoAnalyses,
                currentEditCount = 1,
                isVoiceEditingFromEditor = true
            ))
            mainViewModel.startEditing(command)
        }
        
        // Возвращаемся на главный экран со speech bubbles
        mainViewModel.navigateTo(MainViewModel.Screen.Main)
    }
    
    /**
     * Сброс к оригинальному плану
     */
    fun resetToOriginal() {
        Log.d(TAG, "resetToOriginal called")
        
        viewModelScope.launch {
            try {
                // Очищаем текущее состояние
                _timelineState.value = TimelineState()
                _editHistory.clear()
                _historyIndex = -1
                
                // Переинициализируем с оригинальными данными
                val originalPlan = _originalPlan
                val originalAnalyses = _originalVideoAnalyses
                val originalVideos = _originalSelectedVideos
                
                if (originalPlan != null && originalVideos.isNotEmpty()) {
                    Log.d(TAG, "Resetting with original plan: ${originalPlan.finalEdit.size} segments")
                    
                    // Временно сбрасываем оригинальные данные, чтобы initializeWithEditPlan сохранил их заново
                    _originalPlan = null
                    _originalVideoAnalyses = null
                    _originalSelectedVideos = emptyList()
                    
                    // Если план пустой, это был ручной режим
                    if (originalPlan.finalEdit.isEmpty()) {
                        initializeManualEdit(originalVideos)
                    } else {
                        // Обычная инициализация с планом
                        initializeWithEditPlan(originalPlan, originalAnalyses ?: emptyMap(), originalVideos)
                    }
                } else {
                    Log.e(TAG, "Cannot reset - no original data available")
                }
                
                // Обновляем состояние
                _editorState.update { state ->
                    state.copy(
                        hasUnsavedChanges = false,
                        editHistory = EditHistoryState(
                            actions = emptyList(),
                            currentIndex = -1,
                            maxHistorySize = MAX_HISTORY_SIZE
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting to original", e)
            }
        }
    }
    
    /**
     * Сохранение финального видео
     */
    suspend fun exportToTempFile(onProgress: (Float) -> Unit): String {
        return try {
            _editorState.update { it.copy(isSaving = true) }
            
            val segments = _timelineState.value.segments
            
            // Создаем временный файл для экспорта
            val tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
            val outputPath = tempFile.absolutePath
            
            videoEditingService.createFinalVideo(
                segments = segments,
                outputPath = outputPath,
                onProgress = onProgress
            )
            
            // Сохраняем путь к временному файлу
            _editorState.update { state ->
                state.copy(
                    tempVideoPath = outputPath,
                    isSaving = false,
                    hasUnsavedChanges = false
                )
            }
            
            Log.d(TAG, "Video exported to temp file: $outputPath")
            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting video", e)
            _editorState.update { it.copy(isSaving = false) }
            throw e
        }
    }
    
    /**
     * Получение обновленного EditPlan
     */
    fun getUpdatedEditPlan(): EditPlan {
        val segments = _timelineState.value.segments
        
        // Создаем обновленный EditPlan на основе текущих сегментов
        val updatedSegments = segments.map { segment ->
            // Находим имя файла для сегмента
            val fileName = _originalSelectedVideos.find { 
                it.uri == segment.sourceVideoUri 
            }?.fileName ?: segment.sourceVideoPath
            
            EditSegment(
                sourceVideo = fileName,
                startTime = segment.startTime,
                endTime = segment.endTime
            )
        }
        
        return EditPlan(updatedSegments)
    }
    
    // Вспомогательные методы
    
    private fun updateSegment(segmentId: String, update: (VideoSegment) -> VideoSegment) {
        _timelineState.update { state ->
            val updatedSegments = state.segments.map { segment ->
                if (segment.id == segmentId) update(segment) else segment
            }
            // Пересчитываем общую длительность
            val newTotalDuration = updatedSegments.sumOf { it.duration.toDouble() }.toFloat()
            
            state.copy(
                segments = updatedSegments,
                totalDuration = newTotalDuration
            )
        }
        
        // Не обновляем видео сразу - только после окончания редактирования
    }
    
    private fun recordAction(action: EditAction) {
        // Удаляем все действия после текущего индекса
        if (_historyIndex < _editHistory.size - 1) {
            _editHistory.subList(_historyIndex + 1, _editHistory.size).clear()
        }
        
        // Добавляем новое действие
        _editHistory.add(action)
        _historyIndex = _editHistory.size - 1
        
        // Ограничиваем размер истории
        if (_editHistory.size > MAX_HISTORY_SIZE) {
            _editHistory.removeAt(0)
            _historyIndex--
        }
        
        _editorState.update { state ->
            state.copy(
                hasUnsavedChanges = true,
                editHistory = EditHistoryState(
                    actions = _editHistory,
                    currentIndex = _historyIndex,
                    maxHistorySize = MAX_HISTORY_SIZE
                )
            )
        }
        
        // Сохраняем состояние после каждого действия
        saveState()
    }
    
    private fun applyAction(action: EditAction) {
        when (action) {
            is EditAction.TrimSegment -> {
                _timelineState.update { state ->
                    state.copy(
                        segments = state.segments.map { segment ->
                            if (segment.id == action.segmentId) {
                                segment.copy(
                                    inPoint = action.newStartTime,
                                    outPoint = action.newEndTime
                                )
                            } else segment
                        }
                    )
                }
            }
            is EditAction.RemoveSegment -> {
                // Apply remove - remove the segment
                _timelineState.update { state ->
                    state.copy(
                        segments = state.segments.filterNot { it.id == action.segment.id }
                    )
                }
            }
            is EditAction.AddSegment -> {
                // Apply add - add the segment at position
                _timelineState.update { state ->
                    val newSegments = state.segments.toMutableList()
                    // Avoid duplicate by checking if segment already exists
                    if (!newSegments.any { it.id == action.segment.id }) {
                        newSegments.add(action.position, action.segment)
                    }
                    state.copy(segments = newSegments)
                }
            }
            is EditAction.MoveSegment -> {
                // Apply move - move from fromPosition to toPosition
                _timelineState.update { state ->
                    val segments = state.segments.toMutableList()
                    // Find the actual current position of the segment
                    val currentIndex = segments.indexOfFirst { it.id == action.segmentId }
                    if (currentIndex != -1) {
                        val segment = segments.removeAt(currentIndex)
                        segments.add(action.toPosition, segment)
                    }
                    state.copy(segments = segments)
                }
            }
            is EditAction.ResetToOriginal -> {
                // Для reset нужно сохранить текущее состояние
                // и восстановить оригинальное
            }
        }
    }
    
    private fun revertAction(action: EditAction) {
        when (action) {
            is EditAction.TrimSegment -> {
                _timelineState.update { state ->
                    state.copy(
                        segments = state.segments.map { segment ->
                            if (segment.id == action.segmentId) {
                                segment.copy(
                                    inPoint = action.oldStartTime,
                                    outPoint = action.oldEndTime
                                )
                            } else segment
                        }
                    )
                }
            }
            is EditAction.RemoveSegment -> {
                // When reverting RemoveSegment, we need to add it back
                _timelineState.update { state ->
                    val newSegments = state.segments.toMutableList()
                    // Avoid duplicate by checking if segment already exists
                    if (!newSegments.any { it.id == action.segment.id }) {
                        newSegments.add(action.position, action.segment)
                    }
                    state.copy(segments = newSegments)
                }
            }
            is EditAction.AddSegment -> {
                // When reverting AddSegment, we need to remove it
                _timelineState.update { state ->
                    state.copy(
                        segments = state.segments.filterNot { it.id == action.segment.id }
                    )
                }
            }
            is EditAction.MoveSegment -> {
                // When reverting MoveSegment, we need to move it back
                _timelineState.update { state ->
                    val segments = state.segments.toMutableList()
                    // Find the actual current position of the segment
                    val currentIndex = segments.indexOfFirst { it.id == action.segmentId }
                    if (currentIndex != -1) {
                        val segment = segments.removeAt(currentIndex)
                        segments.add(action.fromPosition, segment)
                    }
                    state.copy(segments = segments)
                }
            }
            is EditAction.ResetToOriginal -> {
                // Восстановить предыдущее состояние
                // которое было сохранено при применении reset
            }
        }
    }
    
    private suspend fun updateTempVideo() {
        try {
            _editorState.update { it.copy(isProcessing = true) }
            
            val segments = _timelineState.value.segments
            if (segments.isNotEmpty()) {
                Log.d("videoeditorclipcraft", "updateTempVideo: Creating preview with ${segments.size} segments")
                
                // Создаем временное видео объединяя все сегменты
                val tempVideoPath = videoEditingService.createTempVideo(segments)
                Log.d("videoeditorclipcraft", "updateTempVideo: Created temp video at: $tempVideoPath")
                
                _editorState.update { state ->
                    state.copy(
                        tempVideoPath = tempVideoPath,
                        isProcessing = false
                    )
                }
            } else {
                Log.d("videoeditorclipcraft", "updateTempVideo: No segments to preview")
                _editorState.update { state ->
                    state.copy(
                        tempVideoPath = null,
                        isProcessing = false
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating temp video", e)
            Log.e("videoeditorclipcraft", "updateTempVideo failed", e)
            _editorState.update { it.copy(isProcessing = false) }
        }
    }
    
    /**
     * Обновление состояния редактора (используется при восстановлении)
     */
    fun refreshEditorState() {
        Log.d(TAG, "Refreshing editor state")
        _editorState.value = _editorState.value.copy(
            timelineState = _timelineState.value
        )
    }
    
    /**
     * Сохранение состояния редактора
     */
    private fun saveState() {
        try {
            // Сохраняем сегменты таймлайна
            val segmentsJson = gson.toJson(_timelineState.value.segments)
            savedStateHandle[KEY_TIMELINE_SEGMENTS] = segmentsJson
            Log.d(TAG, "Saved ${_timelineState.value.segments.size} segments")
            
            // Сохраняем оригинальный план
            _originalPlan?.let {
                savedStateHandle[KEY_ORIGINAL_PLAN] = gson.toJson(it)
            }
            
            // Сохраняем оригинальные анализы
            _originalVideoAnalyses?.let {
                savedStateHandle[KEY_ORIGINAL_ANALYSES] = gson.toJson(it)
            }
            
            // Сохраняем историю редактирования
            savedStateHandle[KEY_EDIT_HISTORY] = gson.toJson(_editHistory)
            savedStateHandle[KEY_HISTORY_INDEX] = _historyIndex
            
            Log.d(TAG, "State saved successfully")
            Log.d("videoeditorclipcraft", "State saved: segments=${_timelineState.value.segments.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving state", e)
        }
    }
    
    /**
     * Восстановление состояния редактора
     */
    private fun restoreState() {
        try {
            // Восстанавливаем сегменты таймлайна
            savedStateHandle.get<String>(KEY_TIMELINE_SEGMENTS)?.let { json ->
                val type = object : TypeToken<List<VideoSegment>>() {}.type
                val segments: List<VideoSegment> = gson.fromJson(json, type)
                val totalDuration = segments.sumOf { it.duration.toDouble() }.toFloat()
                
                // Restore zoom to default 100%
                val restoredZoom = 1.0f
                
                _timelineState.value = TimelineState(
                    segments = segments,
                    totalDuration = totalDuration,
                    zoomLevel = restoredZoom
                )
                
                // Обновляем состояние редактора
                _editorState.value = _editorState.value.copy(
                    timelineState = _timelineState.value
                )
                
                Log.d(TAG, "Restored ${segments.size} segments from saved state")
                Log.d("videoeditorclipcraft", "Restored timeline: ${segments.size} segments, totalDuration=$totalDuration, zoom=$restoredZoom")
            }
            
            // Восстанавливаем оригинальный план
            savedStateHandle.get<String>(KEY_ORIGINAL_PLAN)?.let { json ->
                _originalPlan = gson.fromJson(json, EditPlan::class.java)
            }
            
            // Восстанавливаем оригинальные анализы
            savedStateHandle.get<String>(KEY_ORIGINAL_ANALYSES)?.let { json ->
                val type = object : TypeToken<Map<String, VideoAnalysis>>() {}.type
                _originalVideoAnalyses = gson.fromJson(json, type)
            }
            
            // Восстанавливаем историю редактирования
            savedStateHandle.get<String>(KEY_EDIT_HISTORY)?.let { json ->
                val type = object : TypeToken<List<EditAction>>() {}.type
                val history: List<EditAction> = gson.fromJson(json, type)
                _editHistory.clear()
                _editHistory.addAll(history)
            }
            
            _historyIndex = savedStateHandle.get<Int>(KEY_HISTORY_INDEX) ?: -1
            
            Log.d(TAG, "State restored successfully")
            Log.d("videoeditorclipcraft", "Final restored state: segments=${_timelineState.value.segments.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring state", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "VideoEditorViewModel onCleared called")
        // Сохраняем состояние перед уничтожением
        saveState()
        // Очищаем временные файлы
        videoEditingService.clearTempFiles()
    }
}