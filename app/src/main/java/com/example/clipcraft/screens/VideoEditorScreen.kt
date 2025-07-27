package com.example.clipcraft.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clipcraft.components.*
import com.example.clipcraft.models.*
import com.example.clipcraft.ui.VideoEditorViewModel
import com.example.clipcraft.ui.MainViewModel
import com.example.clipcraft.components.EmbeddedVideoPlayer
import com.example.clipcraft.components.VideoTimelineSimple
import com.example.clipcraft.components.VideoEditorTutorial
import com.example.clipcraft.components.CompositeVideoPlayer
import com.example.clipcraft.components.EditVideoDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    editPlan: EditPlan,
    videoAnalyses: Map<String, VideoAnalysis>,
    selectedVideos: List<SelectedVideo>,
    onSave: (String, EditPlan) -> Unit,
    onShare: (String) -> Unit,
    onEditWithVoice: () -> Unit,
    onCreateNew: () -> Unit,
    onExit: () -> Unit,
    mainViewModel: MainViewModel,
    viewModel: VideoEditorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val timelineState by viewModel.timelineState.collectAsStateWithLifecycle()
    
    var showResetDialog by remember { mutableStateOf(false) }
    var showSaveProgress by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableStateOf(0f) }
    
    // Tutorial state - показываем при каждом открытии редактора
    val sharedPrefs = context.getSharedPreferences("clipcraft_prefs", Context.MODE_PRIVATE)
    val hasShownVideoEditorTutorial = sharedPrefs.getBoolean("video_editor_tutorial_shown", false)
    val isManualMode = editPlan.finalEdit.isEmpty() && selectedVideos.isNotEmpty()
    var showTutorial by remember { mutableStateOf(true) } // Всегда показываем туториал
    
    // Debug logging for tutorial
    LaunchedEffect(Unit) {
        Log.d("VideoEditorTutorial", "hasShownVideoEditorTutorial: $hasShownVideoEditorTutorial")
        Log.d("VideoEditorTutorial", "isManualMode: $isManualMode")
        Log.d("VideoEditorTutorial", "showTutorial initial value: $showTutorial")
    }
    
    var showVoiceEditDialog by remember { mutableStateOf(false) }
    var pendingVoiceCommand by remember { mutableStateOf("") }
    
    // Состояние для показа индикатора автосохранения
    var showAutoSaveIndicator by remember { mutableStateOf(false) }
    
    // Инициализация редактора
    LaunchedEffect(editPlan) {
        Log.d("videoeditorclipcraft", "VideoEditorScreen: Initializing with editPlan containing ${editPlan.finalEdit.size} segments")
        Log.d("videoeditorclipcraft", "VideoEditorScreen: videoAnalyses contains ${videoAnalyses.size} entries")
        Log.d("videoeditorclipcraft", "VideoEditorScreen: selectedVideos contains ${selectedVideos.size} videos")
        
        // Логируем детали каждого сегмента в плане
        editPlan.finalEdit.forEachIndexed { index, segment ->
            Log.d("videoeditorclipcraft", "VideoEditorScreen: EditPlan segment $index:")
            Log.d("videoeditorclipcraft", "  sourceVideo: ${segment.sourceVideo}")
            Log.d("videoeditorclipcraft", "  startTime: ${segment.startTime}")
            Log.d("videoeditorclipcraft", "  endTime: ${segment.endTime}")
        }
        
        // Логируем выбранные видео
        selectedVideos.forEachIndexed { index, video ->
            Log.d("videoeditorclipcraft", "VideoEditorScreen: Selected video $index:")
            Log.d("videoeditorclipcraft", "  fileName: ${video.fileName}")
            Log.d("videoeditorclipcraft", "  uri: ${video.uri}")
        }
        
        viewModel.initializeWithEditPlan(editPlan, videoAnalyses, selectedVideos)
        
        // Обновляем состояние для отображения восстановленных сегментов
        viewModel.refreshEditorState()
    }
    
    // Лаунчер для выбора видео
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { videoUri ->
            Log.d("videoeditorclipcraft", "VideoEditorScreen: Video selected from picker: $videoUri")
            // Определяем позицию вставки
            val currentPosition = timelineState.currentPosition
            val segments = timelineState.segments
            
            // Находим ближайший сегмент
            var insertIndex = 0
            var cumTime = 0f
            for ((index, segment) in segments.withIndex()) {
                if (currentPosition <= cumTime + segment.duration / 2) {
                    insertIndex = index
                    break
                }
                cumTime += segment.duration
                insertIndex = index + 1
            }
            
            val insertPosition = InsertPosition(
                index = insertIndex,
                side = if (currentPosition < cumTime) InsertSide.BEFORE else InsertSide.AFTER
            )
            
            Log.d("videoeditorclipcraft", "VideoEditorScreen: Adding segment at position $insertIndex")
            viewModel.addSegment(videoUri, insertPosition)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Верхняя панель с заголовком
        TopAppBar(
            title = { 
                Text(
                    "Редактор видео",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    // Автосохранение при выходе, если есть изменения
                    if (timelineState.segments.isNotEmpty()) {
                        showAutoSaveIndicator = true
                        coroutineScope.launch {
                            try {
                                Log.d("VideoEditorScreen", "Auto-saving on exit")
                                val tempPath = viewModel.exportToTempFile { progress ->
                                    // Игнорируем прогресс при автосохранении
                                }
                                val updatedEditPlan = viewModel.getUpdatedEditPlan()
                                onSave(tempPath, updatedEditPlan)
                            } catch (e: Exception) {
                                Log.e("VideoEditorScreen", "Failed to auto-save", e)
                                // Все равно выходим
                                showAutoSaveIndicator = false
                                onExit()
                            }
                        }
                    } else {
                        onExit()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                // Кнопка для показа туториала
                IconButton(onClick = { 
                    showTutorial = true
                }) {
                    Icon(Icons.Default.Help, contentDescription = "Помощь")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        // Видеоплеер (уменьшенный для избежания перекрытия навигацией)
        Box(
            modifier = Modifier
                .weight(0.6f)  // Уменьшаем размер превью
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            // Используем CompositeVideoPlayer для бесшовного воспроизведения
            if (timelineState.segments.isNotEmpty()) {
                CompositeVideoPlayer(
                    segments = timelineState.segments,
                    currentPosition = timelineState.currentPosition,
                    isPlaying = timelineState.isPlaying,
                    onPositionChange = { position ->
                        viewModel.updatePlayheadPosition(position)
                    },
                    onPlayingStateChanged = { isPlaying ->
                        viewModel.setPlayingState(isPlaying)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback на обычный плеер если нет сегментов
                Log.d("videoeditorclipcraft", "VideoEditorScreen: tempVideoPath = ${editorState.tempVideoPath}")
                editorState.tempVideoPath?.let { videoPath ->
                    Log.d("videoeditorclipcraft", "VideoEditorScreen: Displaying video from $videoPath")
                    val videoUri = Uri.parse(videoPath)
                    Log.d("videoeditorclipcraft", "VideoEditorScreen: Parsed URI = $videoUri")
                    EmbeddedVideoPlayer(
                        videoUri = videoUri,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Log.d("videoeditorclipcraft", "VideoEditorScreen: No video to display")
            }
            
            // Индикатор обработки
            if (editorState.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        // Панель управления таймлайном - новый порядок кнопок
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Отменить все изменения (Reset)
            IconButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = "Отменить все",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Undo
            IconButton(
                onClick = { viewModel.undo() },
                enabled = editorState.editHistory.canUndo,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Отменить",
                    tint = if (editorState.editHistory.canUndo) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Play/Pause button
            IconButton(
                onClick = { viewModel.togglePlayback() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (timelineState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (timelineState.isPlaying) "Пауза" else "Воспроизвести",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Кнопка добавления видео
            IconButton(
                onClick = { videoPickerLauncher.launch("video/*") },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Добавить видео",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Redo
            IconButton(
                onClick = { viewModel.redo() },
                enabled = editorState.editHistory.canRedo,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Redo,
                    contentDescription = "Повторить",
                    tint = if (editorState.editHistory.canRedo) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Удалить выбранный сегмент
            IconButton(
                onClick = {
                    timelineState.selectedSegmentId?.let { segmentId ->
                        viewModel.deleteSegment(segmentId)
                    }
                },
                enabled = timelineState.selectedSegmentId != null,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить сегмент",
                    tint = if (timelineState.selectedSegmentId != null)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Сбросить масштаб
            IconButton(
                onClick = { viewModel.updateZoomLevel(1.0f) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.ZoomOutMap,
                    contentDescription = "Сбросить масштаб (100%)",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Таймлайн с новой функциональностью
        Log.d("videoeditorclipcraft", "VideoEditorScreen: Rendering timeline with ${timelineState.segments.size} segments")
        VideoTimelineSimple(
            segments = timelineState.segments,
            currentPosition = timelineState.currentPosition,
            isPlaying = timelineState.isPlaying,
            zoomLevel = timelineState.zoomLevel,
            selectedSegmentId = timelineState.selectedSegmentId,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            onSegmentClick = { segment ->
                Log.d("videoeditorclipcraft", "VideoEditorScreen: Segment clicked with id: ${segment.id}")
                viewModel.selectSegment(
                    if (segment.id == timelineState.selectedSegmentId) null else segment.id
                )
            },
            onSegmentDelete = { segmentId ->
                viewModel.deleteSegment(segmentId)
            },
            onSegmentReorder = { fromIndex, toIndex ->
                val segmentId = timelineState.segments[fromIndex].id
                viewModel.moveSegment(segmentId, fromIndex, toIndex)
                viewModel.snapSegmentsTogether()
            },
            onSegmentTrim = { segmentId, deltaTime, isStart ->
                if (isStart) {
                    viewModel.trimSegmentStart(segmentId, deltaTime)
                } else {
                    viewModel.trimSegmentEnd(segmentId, deltaTime)
                }
                // Не вызываем snapSegmentsTogether при trim, чтобы избежать движения соседних сегментов
            },
            onPositionChange = { position ->
                viewModel.updatePlayheadPosition(position)
            },
            onZoomChange = { newZoom ->
                viewModel.updateZoomLevel(newZoom)
            }
        )
        
        // Нижняя панель с кнопками
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp), // Отступ от нижней навигации
            shape = MaterialTheme.shapes.large.copy(
                bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Первый ряд кнопок
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Сохранить
                    Button(
                        onClick = {
                            showSaveProgress = true
                            
                            coroutineScope.launch {
                                try {
                                    // Экспортируем во временный файл
                                    val tempPath = viewModel.exportToTempFile { progress ->
                                        saveProgress = progress
                                    }
                                    showSaveProgress = false
                                    
                                    // Получаем обновленный план
                                    val updatedEditPlan = viewModel.getUpdatedEditPlan()
                                    
                                    // Передаем путь к временному файлу и обновленный план
                                    onSave(tempPath, updatedEditPlan)
                                } catch (e: Exception) {
                                    showSaveProgress = false
                                    Log.e("VideoEditorScreen", "Failed to save video", e)
                                }
                            }
                        },
                        modifier = Modifier.widthIn(min = 200.dp),
                        enabled = !editorState.isProcessing && !editorState.isSaving
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сохранить")
                    }
                }
                
                // Второй ряд кнопок
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Редактировать голосом
                    OutlinedButton(
                        onClick = { showVoiceEditDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Голосом")
                    }
                    
                    // Создать новое
                    OutlinedButton(
                        onClick = onCreateNew,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Новое")
                    }
                    
                    // Выйти
                    OutlinedButton(
                        onClick = {
                            // Автосохранение при выходе, если есть изменения
                            if (timelineState.segments.isNotEmpty()) {
                                showAutoSaveIndicator = true
                                coroutineScope.launch {
                                    try {
                                        Log.d("VideoEditorScreen", "Auto-saving on exit (button)")
                                        val tempPath = viewModel.exportToTempFile { progress ->
                                            // Игнорируем прогресс при автосохранении
                                        }
                                        val updatedEditPlan = viewModel.getUpdatedEditPlan()
                                        onSave(tempPath, updatedEditPlan)
                                    } catch (e: Exception) {
                                        Log.e("VideoEditorScreen", "Failed to auto-save", e)
                                        // Все равно выходим
                                        showAutoSaveIndicator = false
                                        onExit()
                                    }
                                }
                            } else {
                                onExit()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выйти")
                    }
                }
            }
        }
    }
    
    // Диалог подтверждения сброса
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сбросить изменения?") },
            text = { 
                Text("Все изменения будут отменены и видео вернется к исходному состоянию.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToOriginal()
                        showResetDialog = false
                    }
                ) {
                    Text("Сбросить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Диалог прогресса сохранения
    if (showSaveProgress) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Сохранение видео") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(progress = saveProgress)
                    Text(
                        "${(saveProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = { }
        )
    }
    
    // Диалог голосового редактирования
    if (showVoiceEditDialog) {
        EditVideoDialog(
            currentCommand = "",
            editPlan = editorState.originalPlan?.finalEdit ?: emptyList(),
            onDismiss = { showVoiceEditDialog = false },
            onConfirm = { command ->
                showVoiceEditDialog = false
                pendingVoiceCommand = command
                // Применяем голосовую команду непосредственно здесь
                coroutineScope.launch {
                    viewModel.applyVoiceCommand(command, editPlan, videoAnalyses, selectedVideos, mainViewModel)
                }
            }
        )
    }
    
    // Туториал для видеоредактора
    VideoEditorTutorial(
        isVisible = showTutorial,
        onDismiss = { 
            showTutorial = false
            // Не сохраняем состояние, чтобы туториал показывался каждый раз
        }
    )
    
    // Временный тестовый диалог для проверки
    if (showTutorial) {
        Log.d("VideoEditorTutorial", "showTutorial is true, showing tutorial component")
    }
    
    // Индикатор автосохранения
    if (showAutoSaveIndicator) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Сохранение видео...") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        "Пожалуйста, подождите",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = { }
        )
    }
}